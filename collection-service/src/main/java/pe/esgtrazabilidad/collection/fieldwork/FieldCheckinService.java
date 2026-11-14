package pe.esgtrazabilidad.collection.fieldwork;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordCommand;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.evidence.EvidenceService;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.ActiveRecyclerGuard;
import pe.esgtrazabilidad.collection.wastetype.WasteTypeCatalog;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/**
 * The recycler's check-in at a neighbour (spec B2: R23, R24, CA-35, CA-36, CA-47). Each check-in
 * creates the official {@link CollectionRecord} and its {@code CollectionRegisteredEvent} in the
 * SAME transaction, through the very use case staff use, so the record's rules (inactive neighbour,
 * blocked association, schedule of that neighbour) are one set of rules, not two.
 *
 * <p>Idempotent by {@code clientId} per recycler: a repeat with the same content answers 200 with
 * the same ids and creates nothing; the same {@code clientId} with other content is COL-020. Same
 * {@code clientId} requests are serialised by a transaction-scoped advisory lock (and the unique
 * index stays as the final net), so two simultaneous ones leave one check-in and one record.
 *
 * <p>Anything that talks to the outside (the photo check in B2) happens BEFORE the transaction
 * opens, never inside it or under a lock.
 */
@Service
public class FieldCheckinService {

    /** The business date of a collection is the date in Lima, never the server's or UTC's (CA-47). */
    static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final NamedParameterJdbcTemplate jdbc;
    private final ActiveRecyclerGuard activeRecycler;
    private final WasteTypeCatalog wasteTypes;
    private final CreateCollectionRecordUseCase createRecord;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final Duration closeTolerance;
    private final EntityManager entityManager;
    private final EvidenceService evidence;

    FieldCheckinService(
            EntityManager entityManager,
            EvidenceService evidence,
            NamedParameterJdbcTemplate jdbc,
            ActiveRecyclerGuard activeRecycler,
            WasteTypeCatalog wasteTypes,
            CreateCollectionRecordUseCase createRecord,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${esg.field.close-tolerance-minutes:5}") long closeToleranceMinutes) {
        this.entityManager = entityManager;
        this.evidence = evidence;
        this.jdbc = jdbc;
        this.activeRecycler = activeRecycler;
        this.wasteTypes = wasteTypes;
        this.createRecord = createRecord;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.closeTolerance = Duration.ofMinutes(closeToleranceMinutes);
    }

    /** What the device sends for one check-in. {@code lat}, {@code lng} and {@code accuracyM} go together. */
    public record CheckinCommand(
            UUID clientId,
            UUID neighborId,
            UUID scheduleId,
            Instant recordedAt,
            Double lat,
            Double lng,
            Double accuracyM,
            BigDecimal weightKg,
            String wasteType,
            String photoKey,
            long deviceSeq) {

        boolean hasCoordinates() {
            return lat != null && lng != null && accuracyM != null;
        }
    }

    public record CheckinResult(UUID checkinId, UUID recordId, boolean created) {
    }

    private record Existing(UUID checkinId, UUID recordId, String contentHash) {
    }

    private record WorkdayState(String status, Instant endedAt) {
    }

    /**
     * @throws ApplicationException COL-019 (recycler not active), COL-014 (waste type), AUTH-019 (coordinates
     *     without location consent), COL-016 (workday not the recycler's), COL-017 (workday closed beyond the
     *     tolerance), COL-015 (schedule not assigned to this recycler, or not that neighbour's), COL-001,
     *     COL-013, COL-009 (the record's own rules), COL-020 ({@code clientId} reused with other content),
     *     COL-018 (photo not available)
     */
    public CheckinResult register(RecyclerClaims claims, UUID workdayId, CheckinCommand command) {
        UUID recyclerId = claims.recyclerId();
        activeRecycler.require(recyclerId);
        if (command.hasCoordinates()) {
            claims.requireLocationConsent();
        }
        wasteTypes.requireActive(command.wasteType());
        String contentHash = contentHash(workdayId, command);

        // A repeat is answered before anything costly (the photo check) happens.
        Optional<CheckinResult> repeat = answerIfExisting(recyclerId, command.clientId(), contentHash);
        if (repeat.isPresent()) {
            return repeat.get();
        }

        if (command.photoKey() != null) {
            // A call to the bucket: made here, before the transaction opens and under no lock (A.5a).
            evidence.verifyForCheckin(recyclerId, command.clientId(), command.photoKey());
        }

        return transaction.execute(status -> persist(claims, workdayId, command, contentHash));
    }

    private CheckinResult persist(RecyclerClaims claims, UUID workdayId, CheckinCommand command, String contentHash) {
        UUID recyclerId = claims.recyclerId();
        lockClientId(recyclerId, command.clientId());
        // Authoritative: another request with this clientId may have committed while we waited.
        Optional<CheckinResult> repeat = answerIfExisting(recyclerId, command.clientId(), contentHash);
        if (repeat.isPresent()) {
            return repeat.get();
        }

        requireOpenEnough(recyclerId, workdayId, command.recordedAt());
        requireAssignedSchedule(recyclerId, command.neighborId(), command.scheduleId());

        LocalDate collectionDate = command.recordedAt().atZone(LIMA).toLocalDate();
        CollectionRecord record = createRecord.create(new CreateCollectionRecordCommand(
                command.neighborId(),
                command.scheduleId(),
                claims.associationId(),
                collectionDate,
                command.weightKg(),
                recyclerId,
                command.wasteType()));

        // JPA defers its INSERTs to commit; the check-in row (plain SQL) references the record, so the
        // record must be in the database first, still inside this same transaction.
        entityManager.flush();

        UUID checkinId = IdGenerator.generate();
        jdbc.update(
                """
                insert into field_checkin (id, client_id, recycler_id, workday_id, device_seq, record_id, neighbor_id,
                    schedule_id, recorded_at, received_at, lat, lng, accuracy_m, waste_type, photo_key, content_hash)
                values (:id, :cid, :rid, :wid, :seq, :record, :neighbor, :schedule, :recorded, :received, :lat, :lng,
                    :acc, :waste, :photo, :hash)
                """,
                new MapSqlParameterSource("id", checkinId)
                        .addValue("cid", command.clientId())
                        .addValue("rid", recyclerId)
                        .addValue("wid", workdayId)
                        .addValue("seq", command.deviceSeq())
                        .addValue("record", record.getId())
                        .addValue("neighbor", command.neighborId())
                        .addValue("schedule", command.scheduleId())
                        .addValue("recorded", Timestamp.from(command.recordedAt()))
                        .addValue("received", Timestamp.from(clock.instant()))
                        .addValue("lat", command.lat())
                        .addValue("lng", command.lng())
                        .addValue("acc", command.accuracyM())
                        .addValue("waste", command.wasteType())
                        .addValue("photo", command.photoKey())
                        .addValue("hash", contentHash));
        return new CheckinResult(checkinId, record.getId(), true);
    }

    public record StoredCheckin(UUID checkinId, UUID recordId, long deviceSeq, Instant recordedAt) {
    }

    /** The workday's check-ins by the phone's sequence and then its clock, never by arrival (CA-47). */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<StoredCheckin> checkinsInOrder(UUID workdayId) {
        return jdbc.query(
                """
                select id, record_id, device_seq, recorded_at from field_checkin
                where workday_id = :wid order by device_seq, recorded_at
                """,
                new MapSqlParameterSource("wid", workdayId),
                (rs, i) -> new StoredCheckin(
                        rs.getObject("id", UUID.class),
                        rs.getObject("record_id", UUID.class),
                        rs.getLong("device_seq"),
                        rs.getTimestamp("recorded_at").toInstant()));
    }

    private Optional<CheckinResult> answerIfExisting(UUID recyclerId, UUID clientId, String contentHash) {
        List<Existing> found = jdbc.query(
                "select id, record_id, content_hash from field_checkin where recycler_id = :rid and client_id = :cid",
                new MapSqlParameterSource("rid", recyclerId).addValue("cid", clientId),
                (rs, i) -> new Existing(
                        rs.getObject("id", UUID.class), rs.getObject("record_id", UUID.class), rs.getString("content_hash")));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Existing existing = found.get(0);
        if (!existing.contentHash().equals(contentHash)) {
            throw new ApplicationException(CollectionErrors.CLIENT_ID_REUSED);
        }
        return Optional.of(new CheckinResult(existing.checkinId(), existing.recordId(), false));
    }

    /** One advisory lock per (recycler, clientId), released at commit or rollback. */
    private void lockClientId(UUID recyclerId, UUID clientId) {
        jdbc.getJdbcOperations()
                .query(
                        "select pg_advisory_xact_lock(hashtextextended(?, 0))",
                        (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                        },
                        recyclerId + ":" + clientId);
    }

    private void requireOpenEnough(UUID recyclerId, UUID workdayId, Instant recordedAt) {
        WorkdayState workday = jdbc.query(
                        "select status, ended_at from workday where id = :id and recycler_id = :rid for share",
                        new MapSqlParameterSource("id", workdayId).addValue("rid", recyclerId),
                        (rs, i) -> new WorkdayState(
                                rs.getString("status"),
                                rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(CollectionErrors.WORKDAY_NOT_FOUND));
        // Same rule as the GPS points: a closed workday still takes what was recorded up to the tolerance after.
        if ("CLOSED".equals(workday.status())
                && workday.endedAt() != null
                && recordedAt.isAfter(workday.endedAt().plus(closeTolerance))) {
            throw new ApplicationException(CollectionErrors.WORKDAY_CLOSED);
        }
    }

    /** The schedule must be assigned to THIS recycler and belong to the neighbour of the check-in. */
    private void requireAssignedSchedule(UUID recyclerId, UUID neighborId, UUID scheduleId) {
        Integer matches = jdbc.queryForObject(
                """
                select count(*) from recycler_assignment a
                join collection_schedule s on s.id = a.schedule_id
                where a.schedule_id = :sid and a.recycler_id = :rid and s.neighbor_id = :nid
                """,
                new MapSqlParameterSource("sid", scheduleId).addValue("rid", recyclerId).addValue("nid", neighborId),
                Integer.class);
        if (matches == null || matches == 0) {
            throw new ApplicationException(CollectionErrors.SCHEDULE_NOT_ASSIGNED);
        }
    }

    /** SHA-256 of the request's own fields: tells a harmless repeat from a clientId reused with other content. */
    static String contentHash(UUID workdayId, CheckinCommand c) {
        String canonical = String.join(
                "|",
                workdayId.toString(),
                c.neighborId().toString(),
                c.scheduleId().toString(),
                c.recordedAt().toString(),
                String.valueOf(c.lat()),
                String.valueOf(c.lng()),
                String.valueOf(c.accuracyM()),
                c.weightKg().stripTrailingZeros().toPlainString(),
                c.wasteType(),
                String.valueOf(c.photoKey()),
                Long.toString(c.deviceSeq()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }
}

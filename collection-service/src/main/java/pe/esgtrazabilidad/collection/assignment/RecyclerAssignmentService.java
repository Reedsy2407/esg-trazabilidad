package pe.esgtrazabilidad.collection.assignment;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.events.publish.DomainEventOutbox;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

/**
 * Assigns schedules to recyclers (spec B2, corrections 1 and 2). A schedule has at most one
 * recycler. Every write here takes one transaction-scoped advisory lock: assignments are rare,
 * staff-driven operations, and serialising them makes the "who had it before" answers (the
 * {@code moved} list, the bulk counts) exact instead of racy. The association always comes from
 * the recycler's own copy, never from the caller.
 *
 * <p>The rows read here belong to other features (neighbour, schedule); they are read by SQL
 * on purpose, as a read model, so no other package's entities are exposed.
 */
@Service
public class RecyclerAssignmentService {

    /** Arbitrary constant key of the advisory lock that serialises assignment writes. */
    private static final long ASSIGNMENT_LOCK_KEY = 7_340_211L;

    private final NamedParameterJdbcTemplate jdbc;
    private final RecyclerStatusCopyRepository recyclers;
    private final DomainEventOutbox outbox;
    private final Clock clock;

    RecyclerAssignmentService(
            NamedParameterJdbcTemplate jdbc,
            RecyclerStatusCopyRepository recyclers,
            DomainEventOutbox outbox,
            Clock clock) {
        this.jdbc = jdbc;
        this.recyclers = recyclers;
        this.outbox = outbox;
        this.clock = clock;
    }

    /** One schedule moved away from another recycler by an explicit PUT. */
    public record Moved(UUID scheduleId, UUID fromRecyclerId) {
    }

    public record ReplaceResult(UUID recyclerId, List<UUID> scheduleIds, List<Moved> moved) {
    }

    public record BulkResult(int assigned, int skippedOtherRecycler, int skippedInactive) {
    }

    public record AssignedSchedule(
            UUID scheduleId,
            DayOfWeek dayOfWeek,
            LocalTime time,
            String scheduleStatus,
            UUID neighborId,
            String neighborName,
            String address,
            String district,
            String neighborStatus) {
    }

    /**
     * Replaces the recycler's list with exactly {@code scheduleIds}; schedules another recycler
     * had are moved here and reported.
     *
     * @throws ApplicationException COL-019 (recycler without an active copy), COL-006 (a schedule
     *     that does not exist or is CANCELLED)
     */
    @Transactional
    public ReplaceResult replace(UUID recyclerId, List<UUID> requested) {
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(requested));
        lock();
        // Read after the lock, so the association written is the one current while we hold it.
        UUID associationId = activeAssociation(recyclerId);

        if (!ids.isEmpty()) {
            Integer found = jdbc.queryForObject(
                    """
                    select count(*) from collection_schedule s
                    join neighbor n on n.id = s.neighbor_id and n.deleted_at is null
                    where s.id in (:ids) and s.status <> 'CANCELLED'
                    """,
                    new MapSqlParameterSource("ids", ids),
                    Integer.class);
            if (found == null || found != ids.size()) {
                throw new ApplicationException(CollectionErrors.SCHEDULE_NOT_FOUND);
            }
        }

        Map<UUID, UUID> before = new HashMap<>();
        if (!ids.isEmpty()) {
            jdbc.query(
                    "select schedule_id, recycler_id from recycler_assignment where schedule_id in (:ids)",
                    new MapSqlParameterSource("ids", ids),
                    rs -> {
                        before.put(rs.getObject("schedule_id", UUID.class), rs.getObject("recycler_id", UUID.class));
                    });
        }

        // What this recycler had and is no longer in the list.
        if (ids.isEmpty()) {
            jdbc.update(
                    "delete from recycler_assignment where recycler_id = :rid", new MapSqlParameterSource("rid", recyclerId));
        } else {
            jdbc.update(
                    "delete from recycler_assignment where recycler_id = :rid and schedule_id not in (:ids)",
                    new MapSqlParameterSource("rid", recyclerId).addValue("ids", ids));
        }

        Instant now = clock.instant();
        List<Moved> moved = new ArrayList<>();
        for (UUID scheduleId : ids) {
            UUID previous = before.get(scheduleId);
            if (recyclerId.equals(previous)) {
                continue; // already assigned to this recycler: nothing changes, no event
            }
            jdbc.update(
                    """
                    insert into recycler_assignment (schedule_id, recycler_id, association_id, assigned_at)
                    values (:sid, :rid, :aid, :at)
                    on conflict (schedule_id) do update
                    set recycler_id = excluded.recycler_id,
                        association_id = excluded.association_id,
                        assigned_at = excluded.assigned_at
                    """,
                    new MapSqlParameterSource("sid", scheduleId)
                            .addValue("rid", recyclerId)
                            .addValue("aid", associationId)
                            .addValue("at", Timestamp.from(now)));
            if (previous != null) {
                moved.add(new Moved(scheduleId, previous));
            }
            outbox.publish(new RecyclerAssignedEvent(
                    IdGenerator.generate(), now, recyclerId, scheduleId, districtOfSchedule(scheduleId)));
        }
        return new ReplaceResult(recyclerId, ids, moved);
    }

    /**
     * Assigns the district's ACTIVE schedules of ACTIVE neighbours that have no recycler yet;
     * the ones another recycler already has are skipped and counted, never moved. Repeating the
     * request changes nothing.
     *
     * @throws ApplicationException COL-010 (district outside Lima and Callao), COL-019
     */
    @Transactional
    public BulkResult bulkAssign(UUID recyclerId, String districtUbigeo) {
        if (!UbigeoCatalog.contains(districtUbigeo)) {
            throw new ApplicationException(CollectionErrors.INVALID_DISTRICT);
        }
        lock();
        UUID associationId = activeAssociation(recyclerId);

        MapSqlParameterSource district = new MapSqlParameterSource("ubigeo", districtUbigeo).addValue("rid", recyclerId);
        String fromDistrict = """
                from collection_schedule s
                join neighbor n on n.id = s.neighbor_id and n.deleted_at is null
                left join recycler_assignment a on a.schedule_id = s.id
                where n.district_ubigeo = :ubigeo
                """;
        Integer skippedInactive = jdbc.queryForObject(
                "select count(*) " + fromDistrict + " and s.status <> 'CANCELLED' and (s.status <> 'ACTIVE' or n.status <> 'ACTIVE')",
                district,
                Integer.class);
        Integer skippedOther = jdbc.queryForObject(
                "select count(*) " + fromDistrict
                        + " and s.status = 'ACTIVE' and n.status = 'ACTIVE' and a.recycler_id is not null and a.recycler_id <> :rid",
                district,
                Integer.class);

        Instant now = clock.instant();
        int assigned = jdbc.update(
                """
                insert into recycler_assignment (schedule_id, recycler_id, association_id, assigned_at)
                select s.id, :rid, :aid, :at
                """
                        + fromDistrict
                        + " and s.status = 'ACTIVE' and n.status = 'ACTIVE' and a.recycler_id is null"
                        + " on conflict (schedule_id) do nothing",
                district.addValue("aid", associationId).addValue("at", Timestamp.from(now)));
        if (assigned > 0) {
            outbox.publish(new RecyclerAssignedEvent(IdGenerator.generate(), now, recyclerId, null, districtUbigeo));
        }
        return new BulkResult(assigned, skippedOther == null ? 0 : skippedOther, skippedInactive == null ? 0 : skippedInactive);
    }

    /** What the recycler has assigned, with the neighbour's contact-free data (staff view). */
    @Transactional(readOnly = true)
    public List<AssignedSchedule> list(UUID recyclerId) {
        // Staff may review what an INACTIVE recycler still has assigned, so no active check here.
        return jdbc.query(
                """
                select s.id as schedule_id, s.day_of_week, s.pickup_time, s.status as schedule_status,
                       n.id as neighbor_id, n.full_name, n.address, n.district, n.status as neighbor_status
                from recycler_assignment a
                join collection_schedule s on s.id = a.schedule_id
                join neighbor n on n.id = s.neighbor_id and n.deleted_at is null
                where a.recycler_id = :rid
                order by s.day_of_week, s.pickup_time, n.full_name
                """,
                new MapSqlParameterSource("rid", recyclerId),
                (rs, i) -> new AssignedSchedule(
                        rs.getObject("schedule_id", UUID.class),
                        DayOfWeek.valueOf(rs.getString("day_of_week")),
                        rs.getObject("pickup_time", LocalTime.class),
                        rs.getString("schedule_status"),
                        rs.getObject("neighbor_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getString("address"),
                        rs.getString("district"),
                        rs.getString("neighbor_status")));
    }

    private UUID activeAssociation(UUID recyclerId) {
        return recyclers
                .findActiveAssociationId(recyclerId)
                .orElseThrow(() -> new ApplicationException(CollectionErrors.RECYCLER_NOT_ACTIVE));
    }

    private void lock() {
        jdbc.getJdbcOperations()
                .query("select pg_advisory_xact_lock(?)", (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                }, ASSIGNMENT_LOCK_KEY);
    }

    private String districtOfSchedule(UUID scheduleId) {
        List<String> found = jdbc.queryForList(
                "select n.district_ubigeo from collection_schedule s join neighbor n on n.id = s.neighbor_id where s.id = :sid",
                new MapSqlParameterSource("sid", scheduleId),
                String.class);
        return found.isEmpty() ? null : found.get(0);
    }
}

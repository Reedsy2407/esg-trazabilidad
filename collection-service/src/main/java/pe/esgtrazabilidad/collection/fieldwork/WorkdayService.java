package pe.esgtrazabilidad.collection.fieldwork;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.ActiveRecyclerGuard;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * Workdays and their GPS points (spec B2: R21, R22, A.5a).
 *
 * <p>A workday is OPEN or CLOSED and only moves OPEN to CLOSED, by a conditional UPDATE
 * ({@code ... where status = 'OPEN'}); closing a closed day answers the same instead of failing.
 * Idempotency is held by unique constraints, used as the final net: a duplicate that loses a race
 * becomes "already exists", never a 500. A recycler only reaches their own workdays (404
 * COL-016 otherwise, which does not reveal that the id exists).
 */
@Service
public class WorkdayService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ActiveRecyclerGuard activeRecycler;
    private final Clock clock;
    private final Duration closeTolerance;

    WorkdayService(
            NamedParameterJdbcTemplate jdbc,
            ActiveRecyclerGuard activeRecycler,
            Clock clock,
            @Value("${esg.field.close-tolerance-minutes:5}") long closeToleranceMinutes) {
        this.jdbc = jdbc;
        this.activeRecycler = activeRecycler;
        this.clock = clock;
        this.closeTolerance = Duration.ofMinutes(closeToleranceMinutes);
    }

    public record OpenResult(UUID workdayId, boolean created) {
    }

    public record CloseResult(UUID workdayId, String status, Instant endedAt) {
    }

    public record PointInput(double lat, double lng, double accuracyM, Instant recordedAt, long deviceSeq) {
    }

    public record PointsResult(int accepted, int duplicates) {
    }

    private record WorkdayState(String status, Instant endedAt) {
    }

    public record StoredPoint(long deviceSeq, Instant recordedAt, double lat, double lng, double accuracyM) {
    }

    /**
     * Opens a workday, or returns the one this recycler already opened with that {@code clientId}.
     * {@code withGps} is the location consent of the token at the moment of opening.
     */
    @Transactional
    public OpenResult open(UUID recyclerId, UUID associationId, boolean withGps, Instant startedAt, UUID clientId) {
        activeRecycler.require(recyclerId);
        List<UUID> inserted = jdbc.query(
                """
                insert into workday (id, recycler_id, association_id, client_id, status, started_at, received_at, with_gps)
                values (:id, :rid, :aid, :cid, 'OPEN', :started, :received, :gps)
                on conflict (recycler_id, client_id) do nothing
                returning id
                """,
                new MapSqlParameterSource("id", IdGenerator.generate())
                        .addValue("rid", recyclerId)
                        .addValue("aid", associationId)
                        .addValue("cid", clientId)
                        .addValue("started", Timestamp.from(startedAt))
                        .addValue("received", Timestamp.from(clock.instant()))
                        .addValue("gps", withGps),
                (rs, i) -> rs.getObject("id", UUID.class));
        if (!inserted.isEmpty()) {
            return new OpenResult(inserted.get(0), true);
        }
        UUID existing = jdbc.queryForObject(
                "select id from workday where recycler_id = :rid and client_id = :cid",
                new MapSqlParameterSource("rid", recyclerId).addValue("cid", clientId),
                UUID.class);
        return new OpenResult(existing, false);
    }

    /** OPEN to CLOSED. A closed workday keeps its first {@code endedAt} and answers the same. */
    @Transactional
    public CloseResult close(UUID recyclerId, UUID workdayId, Instant endedAt) {
        activeRecycler.require(recyclerId);
        MapSqlParameterSource params = new MapSqlParameterSource("id", workdayId)
                .addValue("rid", recyclerId)
                .addValue("ended", Timestamp.from(endedAt));
        // greatest(): a phone clock behind the start can never produce an end before it.
        jdbc.update(
                """
                update workday set status = 'CLOSED', ended_at = greatest(:ended, started_at)
                where id = :id and recycler_id = :rid and status = 'OPEN'
                """,
                params);
        return jdbc.query(
                        "select id, status, ended_at from workday where id = :id and recycler_id = :rid",
                        params,
                        (rs, i) -> new CloseResult(
                                rs.getObject("id", UUID.class),
                                rs.getString("status"),
                                rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(CollectionErrors.WORKDAY_NOT_FOUND));
    }

    /**
     * Stores a batch of points; the ones already stored (same {@code recordedAt} in the workday)
     * are ignored and counted. A closed workday still takes points recorded up to the tolerance
     * after its closing; a batch with anything later is refused whole (COL-017).
     */
    @Transactional
    public PointsResult addPoints(UUID recyclerId, UUID workdayId, List<PointInput> points) {
        activeRecycler.require(recyclerId);
        // Shared row lock until commit: a concurrent close waits, so the tolerance below is
        // judged against a closing that cannot change underneath this batch.
        WorkdayState workday = jdbc.query(
                        "select status, ended_at from workday where id = :id and recycler_id = :rid for share",
                        new MapSqlParameterSource("id", workdayId).addValue("rid", recyclerId),
                        (rs, i) -> new WorkdayState(
                                rs.getString("status"),
                                rs.getTimestamp("ended_at") == null ? null : rs.getTimestamp("ended_at").toInstant()))
                .stream()
                .findFirst()
                .orElseThrow(() -> new ApplicationException(CollectionErrors.WORKDAY_NOT_FOUND));
        if ("CLOSED".equals(workday.status()) && workday.endedAt() != null) {
            // Inclusive: a point exactly at endedAt + tolerance still counts ("up to 5 minutes").
            Instant limit = workday.endedAt().plus(closeTolerance);
            if (points.stream().anyMatch(p -> p.recordedAt().isAfter(limit))) {
                throw new ApplicationException(CollectionErrors.WORKDAY_CLOSED);
            }
        }
        if (points.isEmpty()) {
            return new PointsResult(0, 0);
        }

        Timestamp received = Timestamp.from(clock.instant());
        int accepted = jdbc.getJdbcOperations().update(
                """
                insert into workday_point (workday_id, recorded_at, device_seq, lat, lng, accuracy_m, received_at)
                select ?, t.rec, t.seq, t.lat, t.lng, t.acc, ?
                from unnest(?::timestamptz[], ?::bigint[], ?::float8[], ?::float8[], ?::float8[])
                     as t(rec, seq, lat, lng, acc)
                on conflict (workday_id, recorded_at) do nothing
                """,
                ps -> {
                    java.sql.Connection connection = ps.getConnection();
                    ps.setObject(1, workdayId);
                    ps.setTimestamp(2, received);
                    ps.setArray(3, array(connection, "timestamptz", points.stream().map(p -> Timestamp.from(p.recordedAt())).toArray()));
                    ps.setArray(4, array(connection, "bigint", points.stream().map(PointInput::deviceSeq).toArray()));
                    ps.setArray(5, array(connection, "float8", points.stream().map(PointInput::lat).toArray()));
                    ps.setArray(6, array(connection, "float8", points.stream().map(PointInput::lng).toArray()));
                    ps.setArray(7, array(connection, "float8", points.stream().map(PointInput::accuracyM).toArray()));
                });
        return new PointsResult(accepted, points.size() - accepted);
    }

    /** The workday's points ordered by the phone's sequence and then its clock, never by arrival. */
    @Transactional(readOnly = true)
    public List<StoredPoint> pointsInOrder(UUID workdayId) {
        return jdbc.query(
                """
                select device_seq, recorded_at, lat, lng, accuracy_m from workday_point
                where workday_id = :id order by device_seq, recorded_at
                """,
                new MapSqlParameterSource("id", workdayId),
                (rs, i) -> new StoredPoint(
                        rs.getLong("device_seq"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("accuracy_m")));
    }

    private static Array array(java.sql.Connection connection, String type, Object[] values) throws java.sql.SQLException {
        return connection.createArrayOf(type, values);
    }
}

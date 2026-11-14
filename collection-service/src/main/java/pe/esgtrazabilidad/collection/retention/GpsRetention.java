package pe.esgtrazabilidad.collection.retention;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.collection.retention.RouteSimplifier.Point;

/**
 * Turns the exact GPS points of old workdays into a route summary and deletes them (spec B2, CA-39).
 *
 * <p>A workday is old when ALL its points are older than the cut-off ({@code now - gpsDays}); one with any
 * newer point waits. Each workday is processed in its own transaction that (1) claims the workday row with
 * {@code FOR UPDATE SKIP LOCKED}, so a second runner (another replica, a manual run during the scheduled one)
 * skips what the first holds instead of doing it twice, (2) writes the summary and (3) deletes the points.
 * Running again right after finds nothing left to do: the job is idempotent and can be run on demand.
 */
@Component
public class GpsRetention {

    private static final Logger log = LoggerFactory.getLogger(GpsRetention.class);

    /** Legs only count points whose reported accuracy is this good (metres) or better. */
    static final double MAX_ACCURACY_FOR_DISTANCE_M = 100;

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final double toleranceMeters;
    private final int maxPolylinePoints;

    GpsRetention(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            @Value("${esg.retention.polyline-tolerance-meters:25}") double toleranceMeters,
            @Value("${esg.retention.polyline-max-points:200}") int maxPolylinePoints) {
        this.jdbc = jdbc;
        this.transaction = new TransactionTemplate(transactionManager);
        this.toleranceMeters = toleranceMeters;
        this.maxPolylinePoints = maxPolylinePoints;
    }

    public record GpsResult(int workdaysSummarized, int pointsDeleted) {
    }

    private record StoredPoint(Instant recordedAt, double lat, double lng, double accuracyM) {
    }

    /** Summarises and deletes the points of every workday whose points are all older than {@code cutoff}. */
    public GpsResult run(Instant cutoff, Instant now) {
        List<UUID> candidates = jdbc.query(
                """
                select w.id from workday w
                where exists (select 1 from workday_point p where p.workday_id = w.id)
                  and not exists (select 1 from workday_point p where p.workday_id = w.id and p.recorded_at >= :cutoff)
                order by w.id
                """,
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)),
                (rs, i) -> rs.getObject("id", UUID.class));
        int summarized = 0;
        int deleted = 0;
        for (UUID workdayId : candidates) {
            Integer removed = transaction.execute(status -> summarizeAndDelete(workdayId, cutoff, now));
            if (removed != null && removed > 0) {
                summarized++;
                deleted += removed;
            }
        }
        log.info("event=gps_retention_run workdays={} points={}", summarized, deleted);
        return new GpsResult(summarized, deleted);
    }

    /**
     * Must run inside a transaction. Package-private so a test can call it directly for the case it protects
     * against (a recent point that arrived after the candidates were listed).
     *
     * @return how many points were deleted; 0 when another runner holds the workday, a recent point has
     *     appeared, or nothing is left
     */
    int summarizeAndDelete(UUID workdayId, Instant cutoff, Instant now) {
        // The claim: the row lock is held until commit; a runner that finds it locked skips the workday.
        // A GPS batch takes a shared lock on the same row (WorkdayService.addPoints), so from here until commit
        // no new point can be inserted: what is checked next stays true.
        List<UUID> claimed = jdbc.query(
                "select id from workday where id = :id for update skip locked",
                new MapSqlParameterSource("id", workdayId),
                (rs, i) -> rs.getObject("id", UUID.class));
        if (claimed.isEmpty()) {
            return 0;
        }
        // The candidate list was read before the claim: a recent point may have arrived since. The workday is
        // summarised only if ALL its points are still older than the cut-off, never just its old ones.
        Boolean allOld = jdbc.queryForObject(
                "select not exists (select 1 from workday_point where workday_id = :id and recorded_at >= :cutoff)",
                new MapSqlParameterSource("id", workdayId).addValue("cutoff", Timestamp.from(cutoff)),
                Boolean.class);
        if (!Boolean.TRUE.equals(allOld)) {
            return 0;
        }
        List<StoredPoint> points = jdbc.query(
                """
                select recorded_at, lat, lng, accuracy_m from workday_point
                where workday_id = :id and recorded_at < :cutoff
                order by device_seq, recorded_at
                """,
                new MapSqlParameterSource("id", workdayId).addValue("cutoff", Timestamp.from(cutoff)),
                (rs, i) -> new StoredPoint(
                        rs.getTimestamp("recorded_at").toInstant(), rs.getDouble("lat"), rs.getDouble("lng"), rs.getDouble("accuracy_m")));
        if (points.isEmpty()) {
            return 0;
        }

        List<Point> accurate = points.stream()
                .filter(p -> p.accuracyM() <= MAX_ACCURACY_FOR_DISTANCE_M)
                .map(p -> new Point(p.lat(), p.lng()))
                .toList();
        double distance = RouteSimplifier.distanceMeters(accurate);
        List<Point> polyline = RouteSimplifier.simplify(accurate, toleranceMeters, maxPolylinePoints);
        Instant first = points.stream().map(StoredPoint::recordedAt).min(Instant::compareTo).orElseThrow();
        Instant last = points.stream().map(StoredPoint::recordedAt).max(Instant::compareTo).orElseThrow();

        // A summary may already exist (points that arrived after an earlier run, which only a workday left OPEN
        // for the whole period can still receive: a closed one refuses points after 5 minutes). Known limit,
        // also in the specification: counts, distance and the time range add up, but the polyline stays the
        // first one, and the leg between the old last point and the new first one is not counted.
        jdbc.update(
                """
                insert into route_summary (workday_id, point_count, distance_m, first_at, last_at, polyline, summarized_at)
                values (:id, :count, :distance, :first, :last, :polyline, :now)
                on conflict (workday_id) do update
                set point_count = route_summary.point_count + excluded.point_count,
                    distance_m = route_summary.distance_m + excluded.distance_m,
                    first_at = least(route_summary.first_at, excluded.first_at),
                    last_at = greatest(route_summary.last_at, excluded.last_at),
                    summarized_at = excluded.summarized_at
                """,
                new MapSqlParameterSource("id", workdayId)
                        .addValue("count", points.size())
                        .addValue("distance", distance)
                        .addValue("first", Timestamp.from(first))
                        .addValue("last", Timestamp.from(last))
                        .addValue("polyline", toJson(polyline))
                        .addValue("now", Timestamp.from(now)));
        return jdbc.update(
                "delete from workday_point where workday_id = :id and recorded_at < :cutoff",
                new MapSqlParameterSource("id", workdayId).addValue("cutoff", Timestamp.from(cutoff)));
    }

    /** A JSON array of [lat, lng] pairs at 5 decimals (about a metre). */
    static String toJson(List<Point> polyline) {
        List<String> pairs = new ArrayList<>();
        for (Point p : polyline) {
            pairs.add(String.format(Locale.ROOT, "[%.5f,%.5f]", p.lat(), p.lng()));
        }
        return "[" + String.join(",", pairs) + "]";
    }

    /** The cut-off for {@code gpsDays} days before {@code now}. */
    static Instant cutoff(Instant now, int gpsDays) {
        return now.minus(Duration.ofDays(gpsDays));
    }
}

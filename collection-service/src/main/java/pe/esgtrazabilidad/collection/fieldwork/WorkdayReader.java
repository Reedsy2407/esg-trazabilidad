package pe.esgtrazabilidad.collection.fieldwork;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Staff read model of workdays (spec A.5c, CA-38). Reads only; nothing here writes, and no storage or
 * network call happens inside its transactions: the signed photo URLs are added afterwards by
 * {@link StaffWorkdayService}.
 */
@Component
class WorkdayReader {

    private static final Logger log = LoggerFactory.getLogger(WorkdayReader.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    WorkdayReader(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    record WorkdayRow(
            UUID workdayId,
            Instant startedAt,
            Instant endedAt,
            String status,
            boolean withGps,
            long checkinCount,
            BigDecimal totalKg,
            long pointCount) {
    }

    record WorkdayPage(List<WorkdayRow> rows, long total) {
    }

    record WorkdayHeader(UUID workdayId, UUID recyclerId, UUID associationId, Instant startedAt, Instant endedAt, boolean withGps) {
    }

    record PointRow(long deviceSeq, Instant recordedAt, double lat, double lng, double accuracyM) {
    }

    record SummaryRow(int pointCount, double distanceM, Instant firstAt, Instant lastAt, List<List<Double>> polyline) {
    }

    record CheckinRow(
            UUID checkinId,
            UUID recordId,
            Instant recordedAt,
            UUID neighborId,
            String neighborName,
            UUID scheduleId,
            BigDecimal weightKg,
            String wasteType,
            Double lat,
            Double lng,
            Double accuracyM,
            String photoKey,
            boolean photoDeleted) {
    }

    @Transactional(readOnly = true)
    WorkdayPage page(UUID recyclerId, Instant from, Instant to, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("rid", recyclerId);
        StringBuilder where = new StringBuilder(" where w.recycler_id = :rid");
        if (from != null) {
            where.append(" and w.started_at >= :from");
            params.addValue("from", Timestamp.from(from));
        }
        if (to != null) {
            where.append(" and w.started_at <= :to");
            params.addValue("to", Timestamp.from(to));
        }
        Long total = jdbc.queryForObject("select count(*) from workday w" + where, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<WorkdayRow> rows = jdbc.query(
                """
                select w.id, w.started_at, w.ended_at, w.status, w.with_gps,
                       (select count(*) from field_checkin c where c.workday_id = w.id) as checkin_count,
                       coalesce((select sum(r.weight_kg) from field_checkin c
                                 join collection_record r on r.id = c.record_id
                                 where c.workday_id = w.id), 0) as total_kg,
                       coalesce(rs.point_count, 0)
                           + (select count(*) from workday_point p where p.workday_id = w.id) as point_count
                from workday w left join route_summary rs on rs.workday_id = w.id
                """
                        + where
                        + " order by w.started_at desc, w.id desc limit :limit offset :offset",
                params,
                (rs, i) -> new WorkdayRow(
                        rs.getObject("id", UUID.class),
                        rs.getTimestamp("started_at").toInstant(),
                        instant(rs.getTimestamp("ended_at")),
                        rs.getString("status"),
                        rs.getBoolean("with_gps"),
                        rs.getLong("checkin_count"),
                        rs.getBigDecimal("total_kg"),
                        rs.getLong("point_count")));
        return new WorkdayPage(rows, total == null ? 0 : total);
    }

    @Transactional(readOnly = true)
    Optional<WorkdayHeader> header(UUID workdayId) {
        return jdbc.query(
                        "select id, recycler_id, association_id, started_at, ended_at, with_gps from workday where id = :id",
                        new MapSqlParameterSource("id", workdayId),
                        (rs, i) -> new WorkdayHeader(
                                rs.getObject("id", UUID.class),
                                rs.getObject("recycler_id", UUID.class),
                                rs.getObject("association_id", UUID.class),
                                rs.getTimestamp("started_at").toInstant(),
                                instant(rs.getTimestamp("ended_at")),
                                rs.getBoolean("with_gps")))
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    long pointTotal(UUID workdayId) {
        Long total = jdbc.queryForObject(
                "select count(*) from workday_point where workday_id = :id", new MapSqlParameterSource("id", workdayId), Long.class);
        return total == null ? 0 : total;
    }

    /** By the phone's sequence and then its clock, never by arrival (A.5a). */
    @Transactional(readOnly = true)
    List<PointRow> points(UUID workdayId, long offset, int limit) {
        return jdbc.query(
                """
                select device_seq, recorded_at, lat, lng, accuracy_m from workday_point
                where workday_id = :id order by device_seq, recorded_at limit :limit offset :offset
                """,
                new MapSqlParameterSource("id", workdayId).addValue("limit", limit).addValue("offset", offset),
                (rs, i) -> new PointRow(
                        rs.getLong("device_seq"),
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getDouble("lat"),
                        rs.getDouble("lng"),
                        rs.getDouble("accuracy_m")));
    }

    @Transactional(readOnly = true)
    Optional<SummaryRow> summary(UUID workdayId) {
        return jdbc.query(
                        "select point_count, distance_m, first_at, last_at, polyline from route_summary where workday_id = :id",
                        new MapSqlParameterSource("id", workdayId),
                        (rs, i) -> new SummaryRow(
                                rs.getInt("point_count"),
                                rs.getDouble("distance_m"),
                                rs.getTimestamp("first_at").toInstant(),
                                rs.getTimestamp("last_at").toInstant(),
                                polyline(rs.getString("polyline"))))
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    List<CheckinRow> checkins(UUID workdayId) {
        return jdbc.query(
                """
                select c.id, c.record_id, c.recorded_at, c.neighbor_id, n.full_name, c.schedule_id, r.weight_kg,
                       c.waste_type, c.lat, c.lng, c.accuracy_m, c.photo_key, c.photo_deleted_at
                from field_checkin c
                join collection_record r on r.id = c.record_id
                join neighbor n on n.id = c.neighbor_id
                where c.workday_id = :id
                order by c.device_seq, c.recorded_at, c.id
                """,
                new MapSqlParameterSource("id", workdayId),
                (rs, i) -> new CheckinRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("record_id", UUID.class),
                        rs.getTimestamp("recorded_at").toInstant(),
                        rs.getObject("neighbor_id", UUID.class),
                        rs.getString("full_name"),
                        rs.getObject("schedule_id", UUID.class),
                        rs.getBigDecimal("weight_kg"),
                        rs.getString("waste_type"),
                        (Double) rs.getObject("lat"),
                        (Double) rs.getObject("lng"),
                        (Double) rs.getObject("accuracy_m"),
                        rs.getString("photo_key"),
                        rs.getTimestamp("photo_deleted_at") != null));
    }

    private List<List<Double>> polyline(String json) {
        try {
            List<List<Double>> parsed = new ArrayList<>();
            objectMapper.readTree(json).forEach(point -> parsed.add(List.of(point.get(0).asDouble(), point.get(1).asDouble())));
            return parsed;
        } catch (Exception exception) {
            // Never the content: it is location data.
            log.warn("event=route_summary_unreadable reason={}", exception.getClass().getSimpleName());
            return List.of();
        }
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

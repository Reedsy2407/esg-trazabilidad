package pe.esgtrazabilidad.collection.assignment;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ACTIVE schedules of ACTIVE neighbours of one district, with who has each one (A.5c). A read model by SQL,
 * like the rest of this package; it writes nothing. The neighbour's phone is not selected.
 */
@Service
class AssignableScheduleService {

    private final NamedParameterJdbcTemplate jdbc;

    AssignableScheduleService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record AssignableNeighbor(UUID id, String fullName, String address, String district) {
    }

    record AssignableSchedule(
            UUID scheduleId, DayOfWeek dayOfWeek, LocalTime time, AssignableNeighbor neighbor, UUID assignedRecyclerId) {
    }

    record AssignablePage(List<AssignableSchedule> content, int page, int size, long totalElements, int totalPages) {
    }

    @Transactional(readOnly = true)
    AssignablePage list(String districtUbigeo, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource("ubigeo", districtUbigeo);
        String from = """
                from collection_schedule s
                join neighbor n on n.id = s.neighbor_id and n.deleted_at is null and n.status = 'ACTIVE'
                left join recycler_assignment a on a.schedule_id = s.id
                where n.district_ubigeo = :ubigeo and s.status = 'ACTIVE'
                """;
        Long total = jdbc.queryForObject("select count(*) " + from, params, Long.class);
        params.addValue("limit", size).addValue("offset", (long) page * size);
        List<AssignableSchedule> rows = jdbc.query(
                """
                select s.id as schedule_id, s.day_of_week, s.pickup_time,
                       n.id as neighbor_id, n.full_name, n.address, n.district, a.recycler_id
                """
                        + from
                        + """
                          order by n.full_name, n.id,
                                   array_position(array['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'], s.day_of_week::text),
                                   s.pickup_time, s.id
                          limit :limit offset :offset
                          """,
                params,
                (rs, i) -> new AssignableSchedule(
                        rs.getObject("schedule_id", UUID.class),
                        DayOfWeek.valueOf(rs.getString("day_of_week")),
                        rs.getObject("pickup_time", LocalTime.class),
                        new AssignableNeighbor(
                                rs.getObject("neighbor_id", UUID.class),
                                rs.getString("full_name"),
                                rs.getString("address"),
                                rs.getString("district")),
                        rs.getObject("recycler_id", UUID.class)));
        long count = total == null ? 0 : total;
        return new AssignablePage(rows, page, size, count, (int) Math.ceil(count / (double) size));
    }
}

package pe.esgtrazabilidad.collection.fieldwork;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * The recycler's route (CA-32): the ACTIVE schedules assigned to this recycler whose neighbour is
 * ACTIVE, each with a reduced neighbour. The neighbour's phone is never selected: the type below
 * has no field for it, so no code path can serialise it.
 */
@Service
public class MyRouteService {

    private final NamedParameterJdbcTemplate jdbc;
    private final RecyclerStatusCopyRepository recyclers;

    MyRouteService(NamedParameterJdbcTemplate jdbc, RecyclerStatusCopyRepository recyclers) {
        this.jdbc = jdbc;
        this.recyclers = recyclers;
    }

    public record RouteNeighbor(UUID id, String fullName, String address, String district) {
    }

    public record RouteStop(UUID scheduleId, DayOfWeek dayOfWeek, LocalTime time, RouteNeighbor neighbor) {
    }

    /**
     * @param recyclerId and @param associationId come from the token, never from the caller
     * @throws ApplicationException COL-019 when the recycler has no active copy (fails closed)
     */
    @Transactional(readOnly = true)
    public List<RouteStop> routeOf(UUID recyclerId, UUID associationId) {
        if (!recyclers.isActive(recyclerId)) {
            throw new ApplicationException(CollectionErrors.RECYCLER_NOT_ACTIVE);
        }
        return jdbc.query(
                """
                select s.id as schedule_id, s.day_of_week, s.pickup_time,
                       n.id as neighbor_id, n.full_name, n.address, n.district
                from recycler_assignment a
                join collection_schedule s on s.id = a.schedule_id and s.status = 'ACTIVE'
                join neighbor n on n.id = s.neighbor_id and n.status = 'ACTIVE' and n.deleted_at is null
                where a.recycler_id = :rid and a.association_id = :aid
                order by s.day_of_week, s.pickup_time, n.full_name, s.id
                """,
                new MapSqlParameterSource("rid", recyclerId).addValue("aid", associationId),
                (rs, i) -> new RouteStop(
                        rs.getObject("schedule_id", UUID.class),
                        DayOfWeek.valueOf(rs.getString("day_of_week")),
                        rs.getObject("pickup_time", LocalTime.class),
                        new RouteNeighbor(
                                rs.getObject("neighbor_id", UUID.class),
                                rs.getString("full_name"),
                                rs.getString("address"),
                                rs.getString("district"))));
    }
}

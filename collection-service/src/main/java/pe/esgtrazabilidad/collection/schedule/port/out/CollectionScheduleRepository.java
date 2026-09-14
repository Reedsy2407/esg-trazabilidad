package pe.esgtrazabilidad.collection.schedule.port.out;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface CollectionScheduleRepository {

    CollectionSchedule save(CollectionSchedule schedule);

    CollectionSchedule update(CollectionSchedule schedule);

    Optional<CollectionSchedule> findById(UUID id);

    /**
     * The ACTIVE schedule (if any) for this neighbor on this day -- at most
     * one can exist, enforced by the partial unique index. Used by
     * CollectionScheduleService.assertNoActiveConflict() for COL-002.
     */
    Optional<CollectionSchedule> findActiveByNeighborIdAndDayOfWeek(UUID neighborId, DayOfWeek dayOfWeek);

    Page<CollectionSchedule> findAll(UUID neighborId, Pageable pageable);
}

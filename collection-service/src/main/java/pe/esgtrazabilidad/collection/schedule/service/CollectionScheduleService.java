package pe.esgtrazabilidad.collection.schedule.service;

import java.time.DayOfWeek;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.in.CancelCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleCommand;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.GetCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.ListCollectionSchedulesUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.PauseCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.in.ReactivateCollectionScheduleUseCase;
import pe.esgtrazabilidad.collection.schedule.port.out.CollectionScheduleRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class CollectionScheduleService
        implements CreateCollectionScheduleUseCase,
                GetCollectionScheduleUseCase,
                ListCollectionSchedulesUseCase,
                PauseCollectionScheduleUseCase,
                CancelCollectionScheduleUseCase,
                ReactivateCollectionScheduleUseCase {

    private final CollectionScheduleRepository repository;
    private final NeighborRepository neighborRepository;

    CollectionScheduleService(CollectionScheduleRepository repository, NeighborRepository neighborRepository) {
        this.repository = repository;
        this.neighborRepository = neighborRepository;
    }

    @Override
    public CollectionSchedule create(CreateCollectionScheduleCommand command) {
        neighborRepository
                .findById(command.neighborId())
                .orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
        assertNoActiveConflict(command.neighborId(), command.dayOfWeek(), null);
        CollectionSchedule schedule =
                CollectionSchedule.create(command.neighborId(), command.dayOfWeek(), command.time());
        return repository.save(schedule);
    }

    @Override
    public CollectionSchedule getById(UUID neighborId, UUID id) {
        return findScoped(neighborId, id);
    }

    @Override
    public Page<CollectionSchedule> list(UUID neighborId, Pageable pageable) {
        return repository.findAll(neighborId, pageable);
    }

    @Override
    public CollectionSchedule pause(UUID neighborId, UUID id) {
        CollectionSchedule schedule = findScoped(neighborId, id);
        try {
            schedule.pause();
        } catch (IllegalStateException e) {
            throw new ApplicationException(CollectionErrors.INVALID_SCHEDULE_TRANSITION);
        }
        return repository.update(schedule);
    }

    @Override
    public CollectionSchedule cancel(UUID neighborId, UUID id) {
        CollectionSchedule schedule = findScoped(neighborId, id);
        try {
            schedule.cancel();
        } catch (IllegalStateException e) {
            throw new ApplicationException(CollectionErrors.INVALID_SCHEDULE_TRANSITION);
        }
        return repository.update(schedule);
    }

    @Override
    public CollectionSchedule reactivate(UUID neighborId, UUID id) {
        CollectionSchedule schedule = findScoped(neighborId, id);
        try {
            schedule.reactivate();
        } catch (IllegalStateException e) {
            throw new ApplicationException(CollectionErrors.INVALID_SCHEDULE_TRANSITION);
        }
        assertNoActiveConflict(schedule.getNeighborId(), schedule.getDayOfWeek(), schedule.getId());
        return repository.update(schedule);
    }

    /**
     * Used by create() and by reactivate() above -- one rule, one place, so
     * the two can't drift.
     */
    void assertNoActiveConflict(UUID neighborId, DayOfWeek dayOfWeek, UUID excludingScheduleId) {
        repository
                .findActiveByNeighborIdAndDayOfWeek(neighborId, dayOfWeek)
                .filter(existing -> !existing.getId().equals(excludingScheduleId))
                .ifPresent(existing -> {
                    throw new ApplicationException(CollectionErrors.SCHEDULE_CONFLICT);
                });
    }

    CollectionSchedule findScoped(UUID neighborId, UUID id) {
        CollectionSchedule schedule = repository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(CollectionErrors.SCHEDULE_NOT_FOUND));
        if (!schedule.getNeighborId().equals(neighborId)) {
            throw new ApplicationException(CollectionErrors.SCHEDULE_NOT_FOUND);
        }
        return schedule;
    }
}

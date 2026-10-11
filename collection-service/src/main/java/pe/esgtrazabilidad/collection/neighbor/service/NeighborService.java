package pe.esgtrazabilidad.collection.neighbor.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.collectionrecord.port.out.CollectionRecordRepository;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborDeletion;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;
import pe.esgtrazabilidad.collection.neighbor.port.in.ChangeNeighborStatusUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.DeleteNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.EditNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.in.EditNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.GetNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.ListNeighborsUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborStatusChangeRepository;
import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.out.CollectionScheduleRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.kernel.ubigeo.District;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

@Service
class NeighborService
        implements CreateNeighborUseCase,
                GetNeighborUseCase,
                ListNeighborsUseCase,
                EditNeighborUseCase,
                ChangeNeighborStatusUseCase,
                DeleteNeighborUseCase {

    private final NeighborRepository repository;
    private final NeighborStatusChangeRepository statusChangeRepository;
    private final CollectionScheduleRepository scheduleRepository;
    private final CollectionRecordRepository recordRepository;

    NeighborService(
            NeighborRepository repository,
            NeighborStatusChangeRepository statusChangeRepository,
            CollectionScheduleRepository scheduleRepository,
            CollectionRecordRepository recordRepository) {
        this.repository = repository;
        this.statusChangeRepository = statusChangeRepository;
        this.scheduleRepository = scheduleRepository;
        this.recordRepository = recordRepository;
    }

    @Override
    public Neighbor create(CreateNeighborCommand command) {
        District district = resolveDistrict(command.districtUbigeo(), command.district());
        Neighbor neighbor = Neighbor.create(command.fullName(), command.phone(), command.address(), district);
        return repository.save(neighbor);
    }

    /**
     * Reads under an exclusive row lock, so an edit can't interleave with a
     * deactivation, a deletion or another edit of the same neighbour: it
     * either goes first, or sees the result (a deleted neighbour is COL-001).
     */
    @Override
    @Transactional
    public Neighbor edit(EditNeighborCommand command) {
        Neighbor neighbor = repository
                .findByIdForUpdate(command.id())
                .orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
        District district = resolveDistrict(command.districtUbigeo(), null);
        neighbor.edit(command.fullName(), command.phone(), command.address(), district);
        return repository.update(neighbor);
    }

    @Override
    @Transactional
    public Neighbor deactivate(UUID id, String reason, CurrentActor actor) {
        Neighbor neighbor = getById(id);
        try {
            neighbor.deactivate();
        } catch (IllegalStateException e) {
            throw new ApplicationException(CollectionErrors.NEIGHBOR_STATUS_UNCHANGED);
        }
        transition(id, NeighborStatus.ACTIVE, NeighborStatus.INACTIVE, reason, actor);
        for (CollectionSchedule schedule : scheduleRepository.findActiveByNeighborId(id)) {
            schedule.pause();
            scheduleRepository.update(schedule);
        }
        return neighbor;
    }

    @Override
    @Transactional
    public Neighbor activate(UUID id, String reason, CurrentActor actor) {
        Neighbor neighbor = getById(id);
        try {
            neighbor.activate();
        } catch (IllegalStateException e) {
            throw new ApplicationException(CollectionErrors.NEIGHBOR_STATUS_UNCHANGED);
        }
        transition(id, NeighborStatus.INACTIVE, NeighborStatus.ACTIVE, reason, actor);
        return neighbor;
    }

    /**
     * The in-memory check above gives the common case a clean COL-012; this
     * compare-and-set closes the race between two concurrent requests, so
     * the history never gets two rows for one transition.
     */
    private void transition(UUID id, NeighborStatus from, NeighborStatus to, String reason, CurrentActor actor) {
        if (!repository.changeStatus(id, from, to)) {
            // Lost to a concurrent request: either it changed the status, or it
            // deleted the neighbour, which must read as "not found".
            getById(id);
            throw new ApplicationException(CollectionErrors.NEIGHBOR_STATUS_UNCHANGED);
        }
        statusChangeRepository.save(NeighborStatusChange.record(id, from, to, reason, actor));
    }

    /**
     * The mark comes first on purpose: it takes the neighbour's row lock, so
     * a collection being recorded at the same moment (which holds a shared
     * lock on that row) commits before the existence check below runs, and
     * then the check sees it and rolls the deletion back.
     */
    @Override
    @Transactional
    public void delete(UUID id, String reason, CurrentActor actor) {
        getById(id);
        NeighborDeletion deletion = NeighborDeletion.of(reason, actor);
        if (!repository.markDeleted(id, deletion)) {
            throw new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND);
        }
        if (recordRepository.existsByNeighborId(id)) {
            throw new ApplicationException(CollectionErrors.NEIGHBOR_HAS_COLLECTIONS);
        }
        for (CollectionSchedule schedule : scheduleRepository.findNotCancelledByNeighborId(id)) {
            schedule.cancel();
            scheduleRepository.update(schedule);
        }
    }

    @Override
    public List<NeighborStatusChange> history(UUID id) {
        getById(id);
        return statusChangeRepository.findByNeighborIdNewestFirst(id);
    }

    @Override
    public Neighbor getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
    }

    @Override
    public Page<Neighbor> list(NeighborStatus status, String districtUbigeo, String district, Pageable pageable) {
        if (districtUbigeo != null && !districtUbigeo.isBlank()) {
            if (!UbigeoCatalog.contains(districtUbigeo)) {
                throw new ApplicationException(CollectionErrors.INVALID_DISTRICT);
            }
            return repository.findAll(status, districtUbigeo, pageable);
        }
        if (district != null && !district.isBlank()) {
            return UbigeoCatalog.findByName(district)
                    .map(match -> repository.findAll(status, match.ubigeo(), pageable))
                    .orElseGet(() -> Page.empty(pageable));
        }
        return repository.findAll(status, null, pageable);
    }

    /** The code wins; the name is the v2 fallback. Neither in the catalog: COL-010. */
    District resolveDistrict(String districtUbigeo, String district) {
        if (districtUbigeo != null && !districtUbigeo.isBlank()) {
            return UbigeoCatalog.find(districtUbigeo)
                    .orElseThrow(() -> new ApplicationException(CollectionErrors.INVALID_DISTRICT));
        }
        return UbigeoCatalog.findByName(district)
                .orElseThrow(() -> new ApplicationException(CollectionErrors.INVALID_DISTRICT));
    }
}

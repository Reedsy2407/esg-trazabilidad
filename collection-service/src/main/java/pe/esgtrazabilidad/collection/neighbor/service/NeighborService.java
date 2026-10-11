package pe.esgtrazabilidad.collection.neighbor.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;
import pe.esgtrazabilidad.collection.neighbor.port.in.ChangeNeighborStatusUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborUseCase;
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
                ChangeNeighborStatusUseCase {

    private final NeighborRepository repository;
    private final NeighborStatusChangeRepository statusChangeRepository;
    private final CollectionScheduleRepository scheduleRepository;

    NeighborService(
            NeighborRepository repository,
            NeighborStatusChangeRepository statusChangeRepository,
            CollectionScheduleRepository scheduleRepository) {
        this.repository = repository;
        this.statusChangeRepository = statusChangeRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @Override
    public Neighbor create(CreateNeighborCommand command) {
        District district = resolveDistrict(command.districtUbigeo(), command.district());
        Neighbor neighbor = Neighbor.create(command.fullName(), command.phone(), command.address(), district);
        return repository.save(neighbor);
    }

    @Override
    public Neighbor edit(EditNeighborCommand command) {
        Neighbor neighbor = getById(command.id());
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
            throw new ApplicationException(CollectionErrors.NEIGHBOR_STATUS_UNCHANGED);
        }
        statusChangeRepository.save(NeighborStatusChange.record(id, from, to, reason, actor));
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

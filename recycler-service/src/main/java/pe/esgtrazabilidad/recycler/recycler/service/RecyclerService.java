package pe.esgtrazabilidad.recycler.recycler.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.events.publish.RecyclerStatusChangedEventPublisher;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.events.RecyclerStatusChangedEvent;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;
import pe.esgtrazabilidad.recycler.recycler.port.in.ActivateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerCommand;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.DeactivateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.GetRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.ListRecyclersUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

@Service
class RecyclerService
        implements CreateRecyclerUseCase,
                GetRecyclerUseCase,
                ListRecyclersUseCase,
                ActivateRecyclerUseCase,
                DeactivateRecyclerUseCase {

    private final RecyclerRepository recyclerRepository;
    private final AssociationRepository associationRepository;

    private final RecyclerStatusChangedEventPublisher eventPublisher;

    RecyclerService(
            RecyclerRepository recyclerRepository,
            AssociationRepository associationRepository,
            RecyclerStatusChangedEventPublisher eventPublisher) {
        this.recyclerRepository = recyclerRepository;
        this.associationRepository = associationRepository;
        this.eventPublisher = eventPublisher;
    }

    // The outbox write must land in the same transaction as the recycler write.
    @Override
    @Transactional
    public Recycler create(CreateRecyclerCommand command) {
        if (associationRepository.findById(command.associationId()).isEmpty()) {
            throw new ApplicationException(RecyclerErrors.ASSOCIATION_NOT_FOUND);
        }
        recyclerRepository.findByDni(command.dni()).ifPresent(existing -> {
            throw new ApplicationException(RecyclerErrors.DUPLICATE_DNI);
        });
        Recycler recycler = Recycler.create(
                command.fullName(), command.dni(), command.phone(), command.associationId());
        Recycler saved = recyclerRepository.save(recycler);
        eventPublisher.publish(RecyclerStatusChangedEvent.from(saved));
        return saved;
    }

    @Override
    public Recycler getById(UUID associationId, UUID id) {
        return findScoped(associationId, id);
    }

    @Override
    public Page<Recycler> list(UUID associationId, RecyclerStatus status, Pageable pageable) {
        return recyclerRepository.findAll(associationId, status, pageable);
    }

    @Override
    @Transactional
    public Recycler activate(UUID associationId, UUID id) {
        Recycler recycler = findScoped(associationId, id);
        try {
            recycler.activate();
        } catch (IllegalStateException e) {
            throw new ApplicationException(RecyclerErrors.INVALID_STATUS_TRANSITION);
        }
        Recycler updated = recyclerRepository.update(recycler);
        eventPublisher.publish(RecyclerStatusChangedEvent.from(updated));
        return updated;
    }

    @Override
    @Transactional
    public Recycler deactivate(UUID associationId, UUID id) {
        Recycler recycler = findScoped(associationId, id);
        try {
            recycler.deactivate();
        } catch (IllegalStateException e) {
            throw new ApplicationException(RecyclerErrors.INVALID_STATUS_TRANSITION);
        }
        Recycler updated = recyclerRepository.update(recycler);
        eventPublisher.publish(RecyclerStatusChangedEvent.from(updated));
        return updated;
    }

    private Recycler findScoped(UUID associationId, UUID id) {
        Recycler recycler = recyclerRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(RecyclerErrors.NOT_FOUND));
        if (!recycler.getAssociationId().equals(associationId)) {
            throw new ApplicationException(RecyclerErrors.NOT_FOUND);
        }
        return recycler;
    }
}

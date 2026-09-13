package pe.esgtrazabilidad.recycler.recycler.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerCommand;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.GetRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.ListRecyclersUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

@Service
class RecyclerService implements CreateRecyclerUseCase, GetRecyclerUseCase, ListRecyclersUseCase {

    private final RecyclerRepository recyclerRepository;
    private final AssociationRepository associationRepository;

    RecyclerService(RecyclerRepository recyclerRepository, AssociationRepository associationRepository) {
        this.recyclerRepository = recyclerRepository;
        this.associationRepository = associationRepository;
    }

    @Override
    public Recycler create(CreateRecyclerCommand command) {
        if (associationRepository.findById(command.associationId()).isEmpty()) {
            throw new ApplicationException(RecyclerErrors.ASSOCIATION_NOT_FOUND);
        }
        recyclerRepository.findByDni(command.dni()).ifPresent(existing -> {
            throw new ApplicationException(RecyclerErrors.DUPLICATE_DNI);
        });
        Recycler recycler = Recycler.create(
                command.fullName(), command.dni(), command.phone(), command.associationId());
        return recyclerRepository.save(recycler);
    }

    @Override
    public Recycler getById(UUID associationId, UUID id) {
        Recycler recycler = recyclerRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(RecyclerErrors.NOT_FOUND));
        if (!recycler.getAssociationId().equals(associationId)) {
            throw new ApplicationException(RecyclerErrors.NOT_FOUND);
        }
        return recycler;
    }

    @Override
    public Page<Recycler> list(UUID associationId, RecyclerStatus status, Pageable pageable) {
        return recyclerRepository.findAll(associationId, status, pageable);
    }
}

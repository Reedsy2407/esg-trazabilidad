package pe.esgtrazabilidad.recycler.association.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;
import pe.esgtrazabilidad.recycler.association.exception.AssociationErrors;
import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationCommand;
import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.GetAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.ListAssociationsUseCase;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

@Service
class AssociationService implements CreateAssociationUseCase, GetAssociationUseCase, ListAssociationsUseCase {

    private final AssociationRepository repository;

    AssociationService(AssociationRepository repository) {
        this.repository = repository;
    }

    @Override
    public Association create(CreateAssociationCommand command) {
        repository.findByRuc(command.ruc()).ifPresent(existing -> {
            throw new ApplicationException(AssociationErrors.DUPLICATE_RUC);
        });
        Association association = Association.create(
                command.name(),
                command.ruc(),
                command.registrationNumber(),
                command.address(),
                command.contactEmail(),
                command.contactPhone());
        return repository.save(association);
    }

    @Override
    public Association getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(AssociationErrors.NOT_FOUND));
    }

    @Override
    public Page<Association> list(AssociationStatus status, Pageable pageable) {
        return repository.findAll(status, pageable);
    }
}

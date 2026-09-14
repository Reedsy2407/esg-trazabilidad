package pe.esgtrazabilidad.collection.neighbor.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.GetNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.ListNeighborsUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class NeighborService implements CreateNeighborUseCase, GetNeighborUseCase, ListNeighborsUseCase {

    private final NeighborRepository repository;

    NeighborService(NeighborRepository repository) {
        this.repository = repository;
    }

    @Override
    public Neighbor create(CreateNeighborCommand command) {
        Neighbor neighbor = Neighbor.create(command.fullName(), command.phone(), command.address(), command.district());
        return repository.save(neighbor);
    }

    @Override
    public Neighbor getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
    }

    @Override
    public Page<Neighbor> list(NeighborStatus status, String district, Pageable pageable) {
        return repository.findAll(status, district, pageable);
    }
}

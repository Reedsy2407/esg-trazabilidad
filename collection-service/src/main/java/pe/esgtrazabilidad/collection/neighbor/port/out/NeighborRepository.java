package pe.esgtrazabilidad.collection.neighbor.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public interface NeighborRepository {

    Neighbor save(Neighbor neighbor);

    Optional<Neighbor> findById(UUID id);

    Page<Neighbor> findAll(NeighborStatus status, String district, Pageable pageable);
}

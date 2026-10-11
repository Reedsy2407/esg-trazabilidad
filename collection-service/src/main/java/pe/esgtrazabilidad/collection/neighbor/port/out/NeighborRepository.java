package pe.esgtrazabilidad.collection.neighbor.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public interface NeighborRepository {

    Neighbor save(Neighbor neighbor);

    /**
     * Moves the neighbour from one status to another only if it still has
     * the first one; false when another request changed it first.
     */
    boolean changeStatus(UUID id, NeighborStatus from, NeighborStatus to);

    /** Writes an already-persisted neighbour (merge, never insert). */
    Neighbor update(Neighbor neighbor);

    Optional<Neighbor> findById(UUID id);

    Page<Neighbor> findAll(NeighborStatus status, String districtUbigeo, Pageable pageable);
}

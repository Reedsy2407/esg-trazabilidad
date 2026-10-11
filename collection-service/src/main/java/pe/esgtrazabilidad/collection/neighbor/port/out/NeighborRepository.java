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

    /**
     * Reads the neighbour holding a shared row lock until the caller's
     * transaction ends: used by writes that depend on the neighbour being
     * ACTIVE (new collection, new or reactivated schedule), so they can't
     * interleave with a deactivation (COL-013).
     */
    Optional<Neighbor> findByIdForShare(UUID id);

    Page<Neighbor> findAll(NeighborStatus status, String districtUbigeo, Pageable pageable);
}

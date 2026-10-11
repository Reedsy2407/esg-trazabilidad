package pe.esgtrazabilidad.collection.neighbor.port.out;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;

public interface NeighborStatusChangeRepository {

    NeighborStatusChange save(NeighborStatusChange change);

    List<NeighborStatusChange> findByNeighborIdNewestFirst(UUID neighborId);
}

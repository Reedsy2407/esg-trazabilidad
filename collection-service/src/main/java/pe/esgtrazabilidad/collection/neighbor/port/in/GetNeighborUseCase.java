package pe.esgtrazabilidad.collection.neighbor.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;

public interface GetNeighborUseCase {

    Neighbor getById(UUID id);
}

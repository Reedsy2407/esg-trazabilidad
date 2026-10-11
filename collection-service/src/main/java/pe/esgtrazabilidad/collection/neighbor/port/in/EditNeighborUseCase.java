package pe.esgtrazabilidad.collection.neighbor.port.in;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;

public interface EditNeighborUseCase {

    Neighbor edit(EditNeighborCommand command);
}

package pe.esgtrazabilidad.collection.neighbor.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public interface ListNeighborsUseCase {

    Page<Neighbor> list(NeighborStatus status, String district, Pageable pageable);
}

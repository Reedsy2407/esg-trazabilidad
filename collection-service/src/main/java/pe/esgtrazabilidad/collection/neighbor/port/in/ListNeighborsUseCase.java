package pe.esgtrazabilidad.collection.neighbor.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public interface ListNeighborsUseCase {

    /**
     * districtUbigeo filters by code (an unknown code is COL-010); district,
     * the v2 filter, is resolved by name against the catalog without case or
     * accents, and a name outside the catalog matches nobody.
     */
    Page<Neighbor> list(NeighborStatus status, String districtUbigeo, String district, Pageable pageable);
}

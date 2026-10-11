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
import pe.esgtrazabilidad.kernel.ubigeo.District;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

@Service
class NeighborService implements CreateNeighborUseCase, GetNeighborUseCase, ListNeighborsUseCase {

    private final NeighborRepository repository;

    NeighborService(NeighborRepository repository) {
        this.repository = repository;
    }

    @Override
    public Neighbor create(CreateNeighborCommand command) {
        District district = resolveDistrict(command.districtUbigeo(), command.district());
        Neighbor neighbor = Neighbor.create(command.fullName(), command.phone(), command.address(), district);
        return repository.save(neighbor);
    }

    @Override
    public Neighbor getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
    }

    @Override
    public Page<Neighbor> list(NeighborStatus status, String districtUbigeo, String district, Pageable pageable) {
        if (districtUbigeo != null && !districtUbigeo.isBlank()) {
            if (!UbigeoCatalog.contains(districtUbigeo)) {
                throw new ApplicationException(CollectionErrors.INVALID_DISTRICT);
            }
            return repository.findAll(status, districtUbigeo, pageable);
        }
        if (district != null && !district.isBlank()) {
            return UbigeoCatalog.findByName(district)
                    .map(match -> repository.findAll(status, match.ubigeo(), pageable))
                    .orElseGet(() -> Page.empty(pageable));
        }
        return repository.findAll(status, null, pageable);
    }

    /** The code wins; the name is the v2 fallback. Neither in the catalog: COL-010. */
    District resolveDistrict(String districtUbigeo, String district) {
        if (districtUbigeo != null && !districtUbigeo.isBlank()) {
            return UbigeoCatalog.find(districtUbigeo)
                    .orElseThrow(() -> new ApplicationException(CollectionErrors.INVALID_DISTRICT));
        }
        return UbigeoCatalog.findByName(district)
                .orElseThrow(() -> new ApplicationException(CollectionErrors.INVALID_DISTRICT));
    }
}

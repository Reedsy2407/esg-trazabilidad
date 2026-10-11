package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;

@Component
class NeighborRepositoryAdapter implements NeighborRepository {

    private final NeighborJpaRepository jpaRepository;

    NeighborRepositoryAdapter(NeighborJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Neighbor save(Neighbor neighbor) {
        return toDomain(jpaRepository.save(toEntity(neighbor)));
    }

    @Override
    public Optional<Neighbor> findByIdForShare(UUID id) {
        return jpaRepository.findByIdForShare(id).map(this::toDomain);
    }

    @Override
    public boolean changeStatus(UUID id, NeighborStatus from, NeighborStatus to) {
        return jpaRepository.updateStatusIfCurrent(id, from.name(), to.name()) == 1;
    }

    @Override
    public Neighbor update(Neighbor neighbor) {
        NeighborEntity existing = NeighborEntity.existing(
                neighbor.getId(),
                neighbor.getFullName(),
                neighbor.getPhone(),
                neighbor.getAddress(),
                neighbor.getDistrict(),
                neighbor.getDistrictUbigeo(),
                neighbor.getStatus().name());
        return toDomain(jpaRepository.save(existing));
    }

    @Override
    public Optional<Neighbor> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<Neighbor> findAll(NeighborStatus status, String districtUbigeo, Pageable pageable) {
        String statusValue = status == null ? null : status.name();
        return jpaRepository
                .findAll(NeighborSpecifications.hasStatus(statusValue)
                        .and(NeighborSpecifications.hasDistrictUbigeo(districtUbigeo)), pageable)
                .map(this::toDomain);
    }

    private NeighborEntity toEntity(Neighbor neighbor) {
        return new NeighborEntity(
                neighbor.getId(),
                neighbor.getFullName(),
                neighbor.getPhone(),
                neighbor.getAddress(),
                neighbor.getDistrict(),
                neighbor.getDistrictUbigeo(),
                neighbor.getStatus().name());
    }

    private Neighbor toDomain(NeighborEntity entity) {
        return Neighbor.reconstruct(
                entity.getId(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getAddress(),
                entity.getDistrict(),
                entity.getDistrictUbigeo(),
                NeighborStatus.valueOf(entity.getStatus()));
    }
}

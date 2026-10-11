package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborStatusChangeRepository;

@Component
class NeighborStatusChangeRepositoryAdapter implements NeighborStatusChangeRepository {

    private final NeighborStatusChangeJpaRepository jpaRepository;

    NeighborStatusChangeRepositoryAdapter(NeighborStatusChangeJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public NeighborStatusChange save(NeighborStatusChange change) {
        return toDomain(jpaRepository.save(new NeighborStatusChangeEntity(
                change.id(),
                change.neighborId(),
                change.fromStatus().name(),
                change.toStatus().name(),
                change.reason(),
                change.actorId(),
                change.actorEmail(),
                change.changedAt())));
    }

    @Override
    public List<NeighborStatusChange> findByNeighborIdNewestFirst(UUID neighborId) {
        return jpaRepository.findByNeighborIdOrderByChangedAtDescIdDesc(neighborId).stream()
                .map(this::toDomain)
                .toList();
    }

    private NeighborStatusChange toDomain(NeighborStatusChangeEntity entity) {
        return new NeighborStatusChange(
                entity.getId(),
                entity.getNeighborId(),
                NeighborStatus.valueOf(entity.getFromStatus()),
                NeighborStatus.valueOf(entity.getToStatus()),
                entity.getReason(),
                entity.getActorId(),
                entity.getActorEmail(),
                entity.getChangedAt());
    }
}

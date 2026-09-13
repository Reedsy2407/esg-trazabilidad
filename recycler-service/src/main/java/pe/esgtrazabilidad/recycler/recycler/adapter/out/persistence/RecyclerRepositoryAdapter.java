package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

@Component
class RecyclerRepositoryAdapter implements RecyclerRepository {

    private final RecyclerJpaRepository jpaRepository;

    RecyclerRepositoryAdapter(RecyclerJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Recycler save(Recycler recycler) {
        return toDomain(jpaRepository.save(toEntity(recycler)));
    }

    @Override
    public Optional<Recycler> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Recycler> findByDni(String dni) {
        return jpaRepository.findByDni(dni).map(this::toDomain);
    }

    @Override
    public Page<Recycler> findAll(UUID associationId, RecyclerStatus status, Pageable pageable) {
        String statusValue = status == null ? null : status.name();
        return jpaRepository
                .findAll(RecyclerSpecifications.hasAssociationId(associationId).and(RecyclerSpecifications.hasStatus(statusValue)), pageable)
                .map(this::toDomain);
    }

    private RecyclerEntity toEntity(Recycler recycler) {
        return new RecyclerEntity(
                recycler.getId(),
                recycler.getFullName(),
                recycler.getDni(),
                recycler.getPhone(),
                recycler.getAssociationId(),
                recycler.getStatus().name());
    }

    private Recycler toDomain(RecyclerEntity entity) {
        return Recycler.reconstruct(
                entity.getId(),
                entity.getFullName(),
                entity.getDni(),
                entity.getPhone(),
                entity.getAssociationId(),
                RecyclerStatus.valueOf(entity.getStatus()));
    }
}

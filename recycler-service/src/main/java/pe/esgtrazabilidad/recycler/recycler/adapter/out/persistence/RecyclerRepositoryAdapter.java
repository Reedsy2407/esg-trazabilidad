package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

@Component
class RecyclerRepositoryAdapter implements RecyclerRepository {

    private final RecyclerJpaRepository jpaRepository;
    private final AssociationRepository associationRepository;

    RecyclerRepositoryAdapter(RecyclerJpaRepository jpaRepository, AssociationRepository associationRepository) {
        this.jpaRepository = jpaRepository;
        this.associationRepository = associationRepository;
    }

    @Override
    public Recycler save(Recycler recycler) {
        if (associationRepository.findById(recycler.getAssociationId()).isEmpty()) {
            throw new ApplicationException(RecyclerErrors.ASSOCIATION_NOT_FOUND);
        }
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

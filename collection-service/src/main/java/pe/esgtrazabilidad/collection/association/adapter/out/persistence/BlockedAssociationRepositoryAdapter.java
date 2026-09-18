package pe.esgtrazabilidad.collection.association.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;
import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;

@Component
class BlockedAssociationRepositoryAdapter implements BlockedAssociationRepository {

    private final BlockedAssociationJpaRepository jpaRepository;

    BlockedAssociationRepositoryAdapter(BlockedAssociationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void block(BlockedAssociation blockedAssociation) {
        UUID associationId = blockedAssociation.getAssociationId();
        BlockedAssociationEntity entity = jpaRepository.existsById(associationId)
                ? BlockedAssociationEntity.existing(associationId, blockedAssociation.getBlockedAt())
                : new BlockedAssociationEntity(associationId, blockedAssociation.getBlockedAt());
        jpaRepository.save(entity);
    }

    @Override
    public void unblock(UUID associationId) {
        // deleteById() throws EmptyResultDataAccessException if the row is
        // missing -- unblock() must be a safe no-op when the association
        // isn't currently blocked (e.g. renewing a certification that never
        // triggered a block), not an error.
        jpaRepository.findById(associationId).ifPresent(jpaRepository::delete);
    }

    @Override
    public boolean isBlocked(UUID associationId) {
        return jpaRepository.existsById(associationId);
    }
}

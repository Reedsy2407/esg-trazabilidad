package pe.esgtrazabilidad.collection.association.port.out;

import java.util.UUID;

import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;

public interface BlockedAssociationRepository {

    void block(BlockedAssociation blockedAssociation);

    void unblock(UUID associationId);

    boolean isBlocked(UUID associationId);
}

package pe.esgtrazabilidad.collection.association.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * collection-service's own, minimal view of "association" -- never a copy
 * of recycler-service's real Association (no name, RUC, contact info). Just
 * the association id and when it was blocked; presence of a row for a given
 * associationId (see BlockedAssociationRepository) IS the blocked state.
 */
public class BlockedAssociation {

    private final UUID associationId;
    private final Instant blockedAt;

    private BlockedAssociation(UUID associationId, Instant blockedAt) {
        this.associationId = associationId;
        this.blockedAt = blockedAt;
    }

    public static BlockedAssociation block(UUID associationId, Instant blockedAt) {
        return new BlockedAssociation(associationId, blockedAt);
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }
}

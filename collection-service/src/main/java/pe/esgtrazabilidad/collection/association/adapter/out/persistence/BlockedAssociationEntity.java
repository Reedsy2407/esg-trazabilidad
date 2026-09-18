package pe.esgtrazabilidad.collection.association.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "blocked_association")
class BlockedAssociationEntity implements Persistable<UUID> {

    @Id
    @Column(name = "association_id")
    private UUID associationId;

    @Column(name = "blocked_at", nullable = false)
    private Instant blockedAt;

    @Transient
    private boolean isNew = false;

    protected BlockedAssociationEntity() {
    }

    BlockedAssociationEntity(UUID associationId, Instant blockedAt) {
        this.associationId = associationId;
        this.blockedAt = blockedAt;
        this.isNew = true;
    }

    /**
     * For re-blocking an association that already has a row (updates
     * blocked_at instead of failing on the PK). Unlike the public
     * constructor (always isNew=true, correct for a first-time block), this
     * produces an entity Spring Data routes through merge() instead of
     * persist().
     */
    static BlockedAssociationEntity existing(UUID associationId, Instant blockedAt) {
        BlockedAssociationEntity entity = new BlockedAssociationEntity();
        entity.associationId = associationId;
        entity.blockedAt = blockedAt;
        entity.isNew = false;
        return entity;
    }

    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return associationId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    UUID getAssociationId() {
        return associationId;
    }

    Instant getBlockedAt() {
        return blockedAt;
    }
}

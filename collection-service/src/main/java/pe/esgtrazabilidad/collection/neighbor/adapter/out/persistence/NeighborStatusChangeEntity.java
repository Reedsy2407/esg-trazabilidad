package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

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
@Table(name = "neighbor_status_change")
class NeighborStatusChangeEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "neighbor_id", nullable = false)
    private UUID neighborId;

    @Column(name = "from_status", nullable = false)
    private String fromStatus;

    @Column(name = "to_status", nullable = false)
    private String toStatus;

    @Column(nullable = false)
    private String reason;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "actor_email")
    private String actorEmail;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Transient
    private boolean isNew = false;

    protected NeighborStatusChangeEntity() {
    }

    NeighborStatusChangeEntity(
            UUID id,
            UUID neighborId,
            String fromStatus,
            String toStatus,
            String reason,
            UUID actorId,
            String actorEmail,
            Instant changedAt) {
        this.id = id;
        this.neighborId = neighborId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.actorId = actorId;
        this.actorEmail = actorEmail;
        this.changedAt = changedAt;
        this.isNew = true;
    }

    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    UUID getNeighborId() {
        return neighborId;
    }

    String getFromStatus() {
        return fromStatus;
    }

    String getToStatus() {
        return toStatus;
    }

    String getReason() {
        return reason;
    }

    UUID getActorId() {
        return actorId;
    }

    String getActorEmail() {
        return actorEmail;
    }

    Instant getChangedAt() {
        return changedAt;
    }
}

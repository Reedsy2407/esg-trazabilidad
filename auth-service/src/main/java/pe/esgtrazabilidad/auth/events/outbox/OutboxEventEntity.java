package pe.esgtrazabilidad.auth.events.outbox;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * Every mutation after creation (processed, failed) is a plain status flip done by an atomic UPDATE in
 * {@link OutboxEventJpaRepository}, never a load-mutate-save round trip through this entity.
 */
@Entity
@Table(name = "outbox_event_auth")
class OutboxEventEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Column(name = "payload_json", nullable = false, columnDefinition = "text")
    private String payloadJson;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = false;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(UUID id, String eventType, String routingKey, String payloadJson, String status, Instant createdAt) {
        this.id = id;
        this.eventType = eventType;
        this.routingKey = routingKey;
        this.payloadJson = payloadJson;
        this.status = status;
        this.createdAt = createdAt;
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

    String getEventType() {
        return eventType;
    }

    String getRoutingKey() {
        return routingKey;
    }

    String getPayloadJson() {
        return payloadJson;
    }

    String getStatus() {
        return status;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}

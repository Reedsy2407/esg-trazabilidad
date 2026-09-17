package pe.esgtrazabilidad.recycler.events.ledger;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * A pure idempotency marker, not a domain object: its existence (PK =
 * event_id) is the whole point. Written once by CollectionRegisteredEventListener
 * (Task 30) inside a try/catch DataIntegrityViolationException, the same
 * pattern already trusted everywhere else in this codebase for TOCTOU races
 * -- never a "SELECT ... WHERE event_id = ?" existence check first, which
 * would itself be racy under concurrent redelivery.
 */
@Entity
@Table(name = "collection_registered_ledger")
public class CollectionRegisteredLedgerEntity implements Persistable<UUID> {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Transient
    private boolean isNew = false;

    protected CollectionRegisteredLedgerEntity() {
    }

    public CollectionRegisteredLedgerEntity(UUID eventId, Instant receivedAt) {
        this.eventId = eventId;
        this.receivedAt = receivedAt;
        this.isNew = true;
    }

    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}

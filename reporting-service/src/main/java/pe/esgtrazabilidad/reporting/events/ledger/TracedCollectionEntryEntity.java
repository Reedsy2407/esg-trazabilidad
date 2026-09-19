package pe.esgtrazabilidad.reporting.events.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/**
 * Doubles as an Inbox-idempotency marker (PK = event_id, same
 * DataIntegrityViolationException-on-redelivery pattern as
 * recycler-service's CollectionRegisteredLedgerEntity) AND the queryable
 * fact table period-scoped sums run against -- a deliberate simplification
 * versus recycler-service's split ledger+atomic-counter design, since this
 * service's own reporting need is period-scoped sums, not one running
 * total (see SPEC-reporting-service.md's Resolved Decisions).
 */
@Entity
@Table(name = "traced_collection_entry")
public class TracedCollectionEntryEntity implements Persistable<UUID> {

    @Id
    @Column(name = "event_id")
    private UUID eventId;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Transient
    private boolean isNew = false;

    protected TracedCollectionEntryEntity() {
    }

    public TracedCollectionEntryEntity(
            UUID eventId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg, Instant receivedAt) {
        this.eventId = eventId;
        this.associationId = associationId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
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

    public UUID getAssociationId() {
        return associationId;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }
}

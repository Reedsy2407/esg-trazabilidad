package pe.esgtrazabilidad.collection.recycler.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Read model of recycler_status_copy; writes go through the atomic upsert in the repository. */
@Entity
@Table(name = "recycler_status_copy")
class RecyclerStatusCopyEntity {

    @Id
    @Column(name = "recycler_id")
    private UUID recyclerId;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "last_occurred_at", nullable = false)
    private Instant lastOccurredAt;

    protected RecyclerStatusCopyEntity() {
    }

    UUID getRecyclerId() {
        return recyclerId;
    }

    UUID getAssociationId() {
        return associationId;
    }

    String getStatus() {
        return status;
    }

    Instant getLastOccurredAt() {
        return lastOccurredAt;
    }
}

package pe.esgtrazabilidad.recycler.recycler.events;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.events.DomainEvent;
import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;

/**
 * A recycler's current status (A.4): emitted on creation (ACTIVE), on every
 * activation or deactivation, and once per recycler by the republish backfill.
 * Consumers keep the copy ordered by {@code occurredAt}.
 */
public record RecyclerStatusChangedEvent(
        UUID eventId, Instant occurredAt, UUID recyclerId, UUID associationId, RecyclerStatus status)
        implements DomainEvent {

    public static RecyclerStatusChangedEvent from(Recycler recycler) {
        return new RecyclerStatusChangedEvent(
                IdGenerator.generate(),
                Instant.now(),
                recycler.getId(),
                recycler.getAssociationId(),
                recycler.getStatus());
    }

    @Override
    public String routingKey() {
        return "recycler.status.changed";
    }
}

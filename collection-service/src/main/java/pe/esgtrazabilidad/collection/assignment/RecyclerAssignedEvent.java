package pe.esgtrazabilidad.collection.assignment;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.events.DomainEvent;

/**
 * A recycler was assigned a schedule (A.4). {@code scheduleId} is set when one schedule was
 * assigned or moved; {@code districtUbigeo} is that schedule's neighbour district, or the
 * district of a bulk assignment (then {@code scheduleId} is null).
 */
public record RecyclerAssignedEvent(
        UUID eventId, Instant occurredAt, UUID recyclerId, UUID scheduleId, String districtUbigeo)
        implements DomainEvent {

    @Override
    public String routingKey() {
        return "collection.recycler.assigned";
    }
}

package pe.esgtrazabilidad.auth.events.publish;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.events.DomainEvent;

/**
 * The retention periods the recycler's consent shows, announced to collection-service so it keeps the
 * photos and the exact GPS points for exactly that long (spec B2, CA-41). Sent on every save by the ADMIN,
 * a real change or not: saving the same values is how collection receives them the first time.
 */
public record RetentionConfigChangedEvent(
        UUID eventId, Instant occurredAt, int gpsDays, int photoMonths, int consentVersion) implements DomainEvent {

    @Override
    public String routingKey() {
        return "auth.retention.changed";
    }
}

package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;
import java.util.UUID;

/**
 * A local, structurally matching copy of auth-service's RetentionConfigChangedEvent (never imported from
 * it): the contract is the JSON, matched by field name. Boxed numbers so a missing field is detected.
 */
record RetentionConfigChangedEvent(
        UUID eventId, Instant occurredAt, Integer gpsDays, Integer photoMonths, Integer consentVersion) {
}

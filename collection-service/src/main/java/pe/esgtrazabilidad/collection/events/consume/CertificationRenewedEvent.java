package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A local, structurally-matching copy of recycler-service's own
 * CertificationRenewedEvent -- never imported from recycler-service, which
 * collection-service never depends on. The two are a JSON wire contract,
 * not a shared Java type; Jackson deserializes by field name.
 */
record CertificationRenewedEvent(
        UUID eventId, Instant occurredAt, UUID certificationId, UUID associationId, LocalDate newExpirationDate) {
}

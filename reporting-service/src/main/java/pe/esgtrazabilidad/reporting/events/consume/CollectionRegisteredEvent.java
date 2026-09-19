package pe.esgtrazabilidad.reporting.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A local, structurally-matching copy of collection-service's own
 * CollectionRegisteredEvent -- never imported from collection-service,
 * which reporting-service never depends on. The two are a JSON wire
 * contract, not a shared Java type; Jackson deserializes by field name.
 * Same shape as recycler-service's own independently-defined copy (Task 30).
 */
record CollectionRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID recordId,
        UUID neighborId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg) {
}

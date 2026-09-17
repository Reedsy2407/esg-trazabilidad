package pe.esgtrazabilidad.recycler.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A local, structurally-matching copy of collection-service's own
 * CollectionRegisteredEvent -- never imported from collection-service,
 * which recycler-service never depends on. The two are a JSON wire
 * contract, not a shared Java type; Jackson deserializes by field name.
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

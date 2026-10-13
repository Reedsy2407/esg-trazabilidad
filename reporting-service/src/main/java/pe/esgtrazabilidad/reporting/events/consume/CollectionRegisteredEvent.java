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
 *
 * v3 (D3): {@code districtUbigeo} is the neighbour's ubigeo when the record was
 * made. It is absent in events published before v3, which Jackson reads as null
 * ("distrito desconocido").
 */
record CollectionRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID recordId,
        UUID neighborId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg,
        String districtUbigeo) {
}

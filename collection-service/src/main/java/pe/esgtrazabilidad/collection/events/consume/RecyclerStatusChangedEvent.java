package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;

/**
 * A local, structurally-matching copy of recycler-service's RecyclerStatusChangedEvent
 * (never imported from it): the contract is the JSON, matched by field name.
 */
record RecyclerStatusChangedEvent(
        UUID eventId, Instant occurredAt, UUID recyclerId, UUID associationId, RecyclerStatus status) {
}

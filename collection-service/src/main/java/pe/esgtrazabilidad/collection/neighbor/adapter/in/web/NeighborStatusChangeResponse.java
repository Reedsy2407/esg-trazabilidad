package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public record NeighborStatusChangeResponse(
        UUID id,
        NeighborStatus fromStatus,
        NeighborStatus toStatus,
        String reason,
        UUID actorId,
        String actorEmail,
        Instant changedAt) {
}

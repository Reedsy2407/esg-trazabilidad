package pe.esgtrazabilidad.collection.neighbor.domain;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.security.CurrentActor;

/** One deactivation or reactivation: why, who (from the JWT) and when. Never edited. */
public record NeighborStatusChange(
        UUID id,
        UUID neighborId,
        NeighborStatus fromStatus,
        NeighborStatus toStatus,
        String reason,
        UUID actorId,
        String actorEmail,
        Instant changedAt) {

    public static final int MAX_REASON_LENGTH = 500;

    public NeighborStatusChange {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (reason.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("El motivo admite hasta 500 caracteres");
        }
    }

    public static NeighborStatusChange record(
            UUID neighborId, NeighborStatus fromStatus, NeighborStatus toStatus, String reason, CurrentActor actor) {
        return new NeighborStatusChange(
                IdGenerator.generate(),
                neighborId,
                fromStatus,
                toStatus,
                reason.strip(),
                actor.id(),
                actor.email(),
                Instant.now());
    }
}

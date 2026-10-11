package pe.esgtrazabilidad.collection.neighbor.domain;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.security.CurrentActor;

/** Why, who (from the JWT) and when a neighbour was logically deleted. */
public record NeighborDeletion(String reason, UUID actorId, String actorEmail, Instant deletedAt) {

    public NeighborDeletion {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("El motivo es obligatorio");
        }
        if (reason.length() > NeighborStatusChange.MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("El motivo admite hasta 500 caracteres");
        }
    }

    public static NeighborDeletion of(String reason, CurrentActor actor) {
        return new NeighborDeletion(reason.strip(), actor.id(), actor.email(), Instant.now());
    }
}

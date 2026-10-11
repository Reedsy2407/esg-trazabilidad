package pe.esgtrazabilidad.collection.neighbor.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.kernel.security.CurrentActor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeighborStatusChangeTest {

    private final CurrentActor actor = new CurrentActor(UUID.randomUUID(), null);

    @Test
    void requiresAReasonOfAtMostFiveHundredCharacters() {
        UUID neighborId = UUID.randomUUID();

        assertThatThrownBy(() -> NeighborStatusChange.record(
                neighborId, NeighborStatus.ACTIVE, NeighborStatus.INACTIVE, "   ", actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NeighborStatusChange.record(
                neighborId, NeighborStatus.ACTIVE, NeighborStatus.INACTIVE, "x".repeat(501), actor))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(NeighborStatusChange.record(
                neighborId, NeighborStatus.ACTIVE, NeighborStatus.INACTIVE, "x".repeat(500), actor).reason())
                .hasSize(500);
    }

    @Test
    void takesWhoFromTheActorAndAllowsAMissingEmail() {
        NeighborStatusChange change = NeighborStatusChange.record(
                UUID.randomUUID(), NeighborStatus.INACTIVE, NeighborStatus.ACTIVE, "Volvió", actor);

        assertThat(change.actorId()).isEqualTo(actor.id());
        assertThat(change.actorEmail()).isNull();
        assertThat(change.changedAt()).isNotNull();
    }
}

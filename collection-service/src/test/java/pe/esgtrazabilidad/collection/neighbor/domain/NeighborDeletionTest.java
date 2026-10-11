package pe.esgtrazabilidad.collection.neighbor.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.kernel.security.CurrentActor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeighborDeletionTest {

    private final CurrentActor actor = new CurrentActor(UUID.randomUUID(), "ana@example.org");

    @Test
    void requiresAReasonOfAtMostFiveHundredCharactersAndStripsIt() {
        assertThatThrownBy(() -> NeighborDeletion.of(" ", actor)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> NeighborDeletion.of("x".repeat(501), actor)).isInstanceOf(IllegalArgumentException.class);
        assertThat(NeighborDeletion.of("  Duplicado  ", actor).reason()).isEqualTo("Duplicado");
    }

    @Test
    void takesWhoFromTheActor() {
        NeighborDeletion deletion = NeighborDeletion.of("Duplicado", actor);

        assertThat(deletion.actorId()).isEqualTo(actor.id());
        assertThat(deletion.actorEmail()).isEqualTo("ana@example.org");
        assertThat(deletion.deletedAt()).isNotNull();
    }
}

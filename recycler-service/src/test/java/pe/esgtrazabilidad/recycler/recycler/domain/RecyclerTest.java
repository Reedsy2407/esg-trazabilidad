package pe.esgtrazabilidad.recycler.recycler.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecyclerTest {

    private final UUID associationId = UUID.randomUUID();

    private Recycler newRecycler() {
        return Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
    }

    @Test
    void createsAnActiveRecyclerWithAGeneratedId() {
        Recycler recycler = newRecycler();

        assertThat(recycler.getId()).isNotNull();
        assertThat(recycler.getStatus()).isEqualTo(RecyclerStatus.ACTIVE);
        assertThat(recycler.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    void rejectsADniThatIsNotEightDigitsLong() {
        assertThatThrownBy(() -> Recycler.create("Juan Pérez", "123", "999999999", associationId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsADniThatContainsNonDigitCharacters() {
        assertThatThrownBy(() -> Recycler.create("Juan Pérez", "1234567a", "999999999", associationId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivateMovesAnActiveRecyclerToInactive() {
        Recycler recycler = newRecycler();

        recycler.deactivate();

        assertThat(recycler.getStatus()).isEqualTo(RecyclerStatus.INACTIVE);
    }

    @Test
    void deactivatingAnAlreadyInactiveRecyclerThrows() {
        Recycler recycler = newRecycler();
        recycler.deactivate();

        assertThatThrownBy(recycler::deactivate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activatingAnAlreadyActiveRecyclerThrows() {
        Recycler recycler = newRecycler();

        assertThatThrownBy(recycler::activate).isInstanceOf(IllegalStateException.class);
    }
}

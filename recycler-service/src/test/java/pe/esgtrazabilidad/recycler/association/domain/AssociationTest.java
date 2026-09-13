package pe.esgtrazabilidad.recycler.association.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssociationTest {

    private Association newAssociation() {
        return Association.create(
                "Asociación de Recicladores Lima",
                "20123456789",
                "REG-001",
                "Av. Siempre Viva 123",
                "contacto@asociacion.pe",
                "999999999");
    }

    @Test
    void createsAnActiveAssociationWithAGeneratedId() {
        Association association = newAssociation();

        assertThat(association.getId()).isNotNull();
        assertThat(association.getStatus()).isEqualTo(AssociationStatus.ACTIVE);
    }

    @Test
    void rejectsARucThatIsNotElevenDigitsLong() {
        assertThatThrownBy(() -> Association.create(
                "Asociación", "123", "REG-001", "Address", "a@b.pe", "999999999"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucThatContainsNonDigitCharacters() {
        assertThatThrownBy(() -> Association.create(
                "Asociación", "1234567890a", "REG-001", "Address", "a@b.pe", "999999999"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void suspendMovesAnActiveAssociationToSuspended() {
        Association association = newAssociation();

        association.suspend();

        assertThat(association.getStatus()).isEqualTo(AssociationStatus.SUSPENDED);
    }

    @Test
    void suspendingAnAlreadySuspendedAssociationThrows() {
        Association association = newAssociation();
        association.suspend();

        assertThatThrownBy(association::suspend).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activatingAnAlreadyActiveAssociationThrows() {
        Association association = newAssociation();

        assertThatThrownBy(association::activate).isInstanceOf(IllegalStateException.class);
    }
}

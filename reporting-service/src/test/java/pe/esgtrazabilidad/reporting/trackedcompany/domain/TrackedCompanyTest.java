package pe.esgtrazabilidad.reporting.trackedcompany.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackedCompanyTest {

    @Test
    void createsAnActiveTrackedCompanyWithAGeneratedId() {
        UUID associationId = UUID.randomUUID();

        TrackedCompany trackedCompany = TrackedCompany.create("Empresa de prueba", "20123456789", associationId);

        assertThat(trackedCompany.getId()).isNotNull();
        assertThat(trackedCompany.getStatus()).isEqualTo(TrackedCompanyStatus.ACTIVE);
        assertThat(trackedCompany.getName()).isEqualTo("Empresa de prueba");
        assertThat(trackedCompany.getRuc()).isEqualTo("20123456789");
        assertThat(trackedCompany.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    void rejectsARucThatIsNotElevenDigitsLong() {
        assertThatThrownBy(() -> TrackedCompany.create("Empresa", "123", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucThatContainsNonDigitCharacters() {
        assertThatThrownBy(() -> TrackedCompany.create("Empresa", "2012345678a", UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullAssociationId() {
        assertThatThrownBy(() -> TrackedCompany.create("Empresa", "20123456789", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingTrackedCompanyWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();

        TrackedCompany trackedCompany =
                TrackedCompany.reconstruct(id, "Empresa", "20123456789", associationId, TrackedCompanyStatus.INACTIVE);

        assertThat(trackedCompany.getId()).isEqualTo(id);
        assertThat(trackedCompany.getStatus()).isEqualTo(TrackedCompanyStatus.INACTIVE);
    }
}

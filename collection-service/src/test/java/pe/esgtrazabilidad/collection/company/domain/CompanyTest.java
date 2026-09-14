package pe.esgtrazabilidad.collection.company.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyTest {

    @Test
    void createsAnActiveCompanyWithAGeneratedId() {
        Company company = Company.create(
                "Empresa de prueba", "20123456789", "a@b.pe", "999999999", "Av. Empresarial 456");

        assertThat(company.getId()).isNotNull();
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        assertThat(company.getName()).isEqualTo("Empresa de prueba");
        assertThat(company.getRuc()).isEqualTo("20123456789");
        assertThat(company.getContactEmail()).isEqualTo("a@b.pe");
        assertThat(company.getContactPhone()).isEqualTo("999999999");
        assertThat(company.getAddress()).isEqualTo("Av. Empresarial 456");
    }

    @Test
    void rejectsARucThatIsNotElevenDigitsLong() {
        assertThatThrownBy(() -> Company.create("Empresa", "123", "a@b.pe", "999999999", "Dirección"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsARucThatContainsNonDigitCharacters() {
        assertThatThrownBy(() -> Company.create("Empresa", "2012345678a", "a@b.pe", "999999999", "Dirección"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingCompanyWithoutGeneratingANewId() {
        java.util.UUID id = java.util.UUID.randomUUID();

        Company company = Company.reconstruct(
                id, "Empresa", "20123456789", "a@b.pe", "999999999", "Dirección", CompanyStatus.INACTIVE);

        assertThat(company.getId()).isEqualTo(id);
        assertThat(company.getStatus()).isEqualTo(CompanyStatus.INACTIVE);
    }
}

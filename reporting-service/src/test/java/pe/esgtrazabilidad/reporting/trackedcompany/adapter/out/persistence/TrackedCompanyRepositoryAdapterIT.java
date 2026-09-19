package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the DB-level unique constraint on ruc fires even bypassing any
 * service-level check -- same discipline as recycler-service's
 * AssociationRepositoryAdapterIT.savingASecondAssociationWithTheSameRucViolatesTheUniqueConstraint.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TrackedCompanyRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TrackedCompanyRepository trackedCompanyRepository;

    @Test
    void savingATrackedCompanyPersistsItWithAGeneratedId() {
        TrackedCompany trackedCompany =
                trackedCompanyRepository.save(TrackedCompany.create("Empresa IT", "20111111111", UUID.randomUUID()));

        assertThat(trackedCompanyRepository.findById(trackedCompany.getId())).isPresent();
    }

    @Test
    void savingASecondTrackedCompanyWithTheSameRucViolatesTheUniqueConstraint() {
        String sharedRuc = "20199999999";
        trackedCompanyRepository.save(TrackedCompany.create("Primera empresa", sharedRuc, UUID.randomUUID()));

        TrackedCompany secondWithSameRuc = TrackedCompany.create("Segunda empresa", sharedRuc, UUID.randomUUID());

        assertThatThrownBy(() -> trackedCompanyRepository.save(secondWithSameRuc))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

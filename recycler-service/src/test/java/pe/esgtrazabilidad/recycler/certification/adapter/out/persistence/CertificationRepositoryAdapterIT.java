package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class CertificationRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CertificationRepository certificationRepository;

    @Test
    void savingACertificationWithANonexistentAssociationViolatesTheForeignKeyConstraint() {
        UUID missingAssociationId = UUID.randomUUID();
        Certification certification = Certification.create(
                missingAssociationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusYears(1));

        assertThatThrownBy(() -> certificationRepository.save(certification))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> {
                    Throwable cause = exception.getCause();
                    assertThat(cause).isInstanceOf(ConstraintViolationException.class);
                    assertThat(((ConstraintViolationException) cause).getConstraintName())
                            .isEqualTo("fk_certification_association");
                });
    }
}

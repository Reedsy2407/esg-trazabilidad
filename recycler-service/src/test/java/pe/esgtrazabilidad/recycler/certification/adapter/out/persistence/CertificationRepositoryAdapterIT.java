package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
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

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
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

    @Autowired
    private AssociationRepository associationRepository;

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

    @Test
    void updatingAnExistingCertificationPersistsTheChangeWithoutDuplicatingTheRow() {
        UUID associationId = associationRepository
                .save(Association.create(
                        "Asociación IT", "20144444444", "REG-IT", "Dirección", "it@test.pe", "999999999"))
                .getId();
        Certification certification = certificationRepository.save(Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusDays(1)));
        UUID id = certification.getId();
        LocalDate newExpirationDate = LocalDate.now().plusYears(1);
        certification.renew(newExpirationDate);

        Certification updated = certificationRepository.update(certification);

        assertThat(updated.getId()).isEqualTo(id);
        Optional<Certification> reloaded = certificationRepository.findById(id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getExpirationDate()).isEqualTo(newExpirationDate);
    }

    @Test
    void notifiedExpiredAtRoundTripsThroughUpdateAndIsResetByRenew() {
        UUID associationId = associationRepository
                .save(Association.create(
                        "Asociación notificada IT", "20133333333", "REG-NOTIF", "Dirección", "it@test.pe",
                        "999999999"))
                .getId();
        Certification certification = certificationRepository.save(Certification.create(
                associationId, "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().minusDays(1)));
        Instant notifiedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        certification.markNotifiedExpired(notifiedAt);
        certificationRepository.update(certification);

        Optional<Certification> afterNotify = certificationRepository.findById(certification.getId());
        assertThat(afterNotify).isPresent();
        assertThat(afterNotify.get().getNotifiedExpiredAt()).isEqualTo(notifiedAt);

        Certification toRenew = afterNotify.get();
        toRenew.renew(LocalDate.now().plusYears(1));
        certificationRepository.update(toRenew);

        Optional<Certification> afterRenew = certificationRepository.findById(certification.getId());
        assertThat(afterRenew).isPresent();
        assertThat(afterRenew.get().getNotifiedExpiredAt()).isNull();
    }
}

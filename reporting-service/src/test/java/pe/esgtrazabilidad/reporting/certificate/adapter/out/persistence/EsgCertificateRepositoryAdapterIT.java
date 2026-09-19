package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the real DB-level EXCLUDE USING gist constraint (RPT-004's
 * backstop) fires on a sequential overlapping period, independent of Task
 * 53's concurrency test (which proves it closes a RACE, not just that a
 * sequential second insert fails) -- same discipline as Task 44's own
 * SigersolSyncRepositoryAdapterIT. esg_certificate.tracked_company_id is a
 * real FK (within-service reference, unlike association_id's deliberate
 * bare-UUID cross-service pattern), so every certificate here is issued for
 * a genuinely persisted TrackedCompany, not a random UUID.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class EsgCertificateRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EsgCertificateRepository repository;

    @Autowired
    private TrackedCompanyRepository trackedCompanyRepository;

    private static final AtomicLong RUC_SEQUENCE = new AtomicLong(20_900_000_000L);

    private TrackedCompany persistedTrackedCompany() {
        String ruc = String.valueOf(RUC_SEQUENCE.incrementAndGet());
        return trackedCompanyRepository.save(TrackedCompany.create("Empresa IT", ruc, UUID.randomUUID()));
    }

    @Test
    void savingACertificateWithLineItemsPersistsBothAtomically() {
        TrackedCompany trackedCompany = persistedTrackedCompany();
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("15.00"),
                new BigDecimal("80.00"));
        List<EsgCertificateLineItem> lineItems = List.of(
                EsgCertificateLineItem.of(certificate.getId(), LocalDate.of(2026, 1, 10), new BigDecimal("10.00")),
                EsgCertificateLineItem.of(certificate.getId(), LocalDate.of(2026, 1, 20), new BigDecimal("5.00")));

        repository.save(certificate, lineItems);

        assertThat(repository.findById(certificate.getId())).isPresent();
        assertThat(repository.findLineItems(certificate.getId())).hasSize(2);
    }

    @Test
    void savingAnOverlappingPeriodForTheSameTrackedCompanyViolatesTheExclusionConstraint() {
        TrackedCompany trackedCompany = persistedTrackedCompany();
        EsgCertificate first = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.TEN, BigDecimal.TEN);
        repository.save(first, List.of());

        EsgCertificate overlapping = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), BigDecimal.TEN, BigDecimal.TEN);

        assertThatThrownBy(() -> repository.save(overlapping, List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingAPeriodSharingExactlyOneBoundaryDayWithAnExistingPeriodViolatesTheExclusionConstraint() {
        // The same decisive boundary case Task 44 established: two periods
        // for the SAME tracked company sharing exactly one day must be
        // rejected by the inclusive daterange bound.
        TrackedCompany trackedCompany = persistedTrackedCompany();
        EsgCertificate first = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.TEN, BigDecimal.TEN);
        repository.save(first, List.of());

        EsgCertificate sharesTheBoundaryDay = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), BigDecimal.TEN, BigDecimal.TEN);

        assertThatThrownBy(() -> repository.save(sharesTheBoundaryDay, List.of()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingTheSamePeriodForADifferentTrackedCompanySucceeds() {
        LocalDate periodStart = LocalDate.of(2026, 1, 1);
        LocalDate periodEnd = LocalDate.of(2026, 1, 31);
        TrackedCompany firstCompany = persistedTrackedCompany();
        TrackedCompany secondCompany = persistedTrackedCompany();
        repository.save(EsgCertificate.issue(firstCompany, periodStart, periodEnd, BigDecimal.TEN, BigDecimal.TEN), List.of());

        EsgCertificate secondCertificate =
                EsgCertificate.issue(secondCompany, periodStart, periodEnd, BigDecimal.TEN, BigDecimal.TEN);

        assertThat(repository.save(secondCertificate, List.of()).getId()).isNotNull();
    }

    @Test
    void existsOverlappingDetectsAPeriodSharingExactlyOneBoundaryDayWithoutTouchingTheExclusionConstraint() {
        // Isolates the service-level existsOverlapping() JPQL from the
        // EXCLUDE constraint entirely -- same discipline Task 45's review
        // established (an HTTP-level test alone can't tell which mechanism
        // actually rejected the second insert, since both map to the same
        // response).
        TrackedCompany trackedCompany = persistedTrackedCompany();
        repository.save(
                EsgCertificate.issue(
                        trackedCompany, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.TEN, BigDecimal.TEN),
                List.of());

        assertThat(repository.existsOverlapping(
                        trackedCompany.getId(), LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
                .isTrue();
        assertThat(repository.existsOverlapping(
                        trackedCompany.getId(), LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .isFalse();
    }
}

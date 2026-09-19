package pe.esgtrazabilidad.reporting.sigersolsync.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the real DB-level EXCLUDE USING gist constraint (RPT-006's
 * backstop) fires on a sequential duplicate/overlapping period, independent
 * of Task 46's concurrency test (which proves it closes a RACE, not just
 * that a second sequential insert fails).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SigersolSyncRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SigersolSyncRepository sigersolSyncRepository;

    @Test
    void existsOverlappingDetectsAPeriodSharingExactlyOneBoundaryDayWithoutTouchingTheExclusionConstraint() {
        // Isolates the service-level existsOverlapping() JPQL from the
        // EXCLUDE USING gist constraint entirely -- no second save() here,
        // so this can't pass merely because the DB backstop happens to
        // reject the insert. A previous review round found that an HTTP-level
        // test alone couldn't tell "existsOverlapping() correctly detected
        // the overlap" apart from "existsOverlapping() missed it, but the DB
        // constraint silently caught the mistake and produced the identical
        // 409 RPT-006 anyway" (SigersolSyncExceptionHandler maps both the
        // service's ApplicationException and the DB's
        // DataIntegrityViolationException to the exact same response shape).
        UUID associationId = UUID.randomUUID();
        sigersolSyncRepository.save(SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("80.00"), null, null));

        assertThat(sigersolSyncRepository.existsOverlapping(
                        associationId, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28)))
                .isTrue();
        assertThat(sigersolSyncRepository.existsOverlapping(
                        associationId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28)))
                .isFalse();
    }

    @Test
    void savingASigersolSyncPersistsItWithAGeneratedId() {
        SigersolSync sigersolSync = sigersolSyncRepository.save(SigersolSync.create(
                UUID.randomUUID(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("80.00"),
                null,
                null));

        assertThat(sigersolSyncRepository.findById(sigersolSync.getId())).isPresent();
    }

    @Test
    void savingAnOverlappingPeriodForTheSameAssociationViolatesTheExclusionConstraint() {
        UUID associationId = UUID.randomUUID();
        sigersolSyncRepository.save(SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("80.00"), null, null));

        SigersolSync overlapping = SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 15), new BigDecimal("75.00"), null, null);

        assertThatThrownBy(() -> sigersolSyncRepository.save(overlapping))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingAPeriodSharingExactlyOneBoundaryDayWithAnExistingPeriodViolatesTheExclusionConstraint() {
        // The decisive boundary case for daterange(..., '[]')'s INCLUSIVE
        // upper bound: Postgres canonicalizes '[2026-01-01,2026-01-31]' to
        // the half-open [2026-01-01,2026-02-01), so a second period starting
        // exactly on the first one's periodEnd (Jan 31) shares that day and
        // MUST be rejected. Neither an overlap in the middle of a period nor
        // a gap of a full month (the other two tests below) can distinguish
        // a correct inclusive bound from an off-by-one exclusive one -- only
        // this exact-shared-day case can, which matters because Tasks 46/50/53
        // all reuse this identical EXCLUDE USING gist mechanism verbatim.
        UUID associationId = UUID.randomUUID();
        sigersolSyncRepository.save(SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("80.00"), null, null));

        SigersolSync sharesTheBoundaryDay = SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), new BigDecimal("75.00"), null, null);

        assertThatThrownBy(() -> sigersolSyncRepository.save(sharesTheBoundaryDay))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savingANonOverlappingAdjacentPeriodForTheSameAssociationSucceeds() {
        UUID associationId = UUID.randomUUID();
        sigersolSyncRepository.save(SigersolSync.create(
                associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("80.00"), null, null));

        SigersolSync adjacent = SigersolSync.create(
                associationId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), new BigDecimal("82.00"), null, null);

        assertThat(sigersolSyncRepository.save(adjacent).getId()).isNotNull();
    }

    @Test
    void savingTheSamePeriodForADifferentAssociationSucceeds() {
        LocalDate periodStart = LocalDate.of(2026, 1, 1);
        LocalDate periodEnd = LocalDate.of(2026, 1, 31);
        sigersolSyncRepository.save(
                SigersolSync.create(UUID.randomUUID(), periodStart, periodEnd, new BigDecimal("80.00"), null, null));

        SigersolSync samePeriodDifferentAssociation =
                SigersolSync.create(UUID.randomUUID(), periodStart, periodEnd, new BigDecimal("60.00"), null, null);

        assertThat(sigersolSyncRepository.save(samePeriodDifferentAssociation).getId()).isNotNull();
    }
}

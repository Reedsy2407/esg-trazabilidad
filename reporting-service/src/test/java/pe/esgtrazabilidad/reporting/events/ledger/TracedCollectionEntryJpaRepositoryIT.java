package pe.esgtrazabilidad.reporting.events.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Schema-only verification (no contrived RED step for a CREATE TABLE, per
 * [[tdd_scope_for_config_fixes]]): proves the table shape, the event_id PK
 * idempotency guarantee, and the period-scoped query the future
 * CertificateService will rely on -- all against a real Postgres.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class TracedCollectionEntryJpaRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TracedCollectionEntryJpaRepository repository;

    @Test
    void savingAnEntryPersistsItKeyedByEventId() {
        UUID eventId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();

        repository.save(new TracedCollectionEntryEntity(
                eventId, associationId, LocalDate.of(2026, 1, 15), new BigDecimal("10.50"), Instant.now()));

        assertThat(repository.findById(eventId)).isPresent();
    }

    @Test
    void savingTheSameEventIdTwiceViolatesThePrimaryKey() {
        UUID eventId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        repository.save(new TracedCollectionEntryEntity(
                eventId, associationId, LocalDate.of(2026, 1, 15), new BigDecimal("10.50"), Instant.now()));

        TracedCollectionEntryEntity redelivered = new TracedCollectionEntryEntity(
                eventId, associationId, LocalDate.of(2026, 1, 15), new BigDecimal("10.50"), Instant.now());

        assertThatThrownBy(() -> repository.saveAndFlush(redelivered))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByAssociationIdAndCollectionDateBetweenOnlyReturnsEntriesInTheRequestedPeriod() {
        UUID associationId = UUID.randomUUID();
        UUID otherAssociationId = UUID.randomUUID();
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, LocalDate.of(2026, 1, 10), new BigDecimal("5.00"), Instant.now()));
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, LocalDate.of(2026, 1, 20), new BigDecimal("3.00"), Instant.now()));
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, LocalDate.of(2026, 2, 1), new BigDecimal("7.00"), Instant.now()));
        // Same date range, DIFFERENT association -- must be excluded. A
        // wrong keyword in the derived query (e.g. matching on date alone)
        // would let this row leak into the result.
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(),
                otherAssociationId,
                LocalDate.of(2026, 1, 15),
                new BigDecimal("99.00"),
                Instant.now()));

        List<TracedCollectionEntryEntity> januaryEntries = repository.findByAssociationIdAndCollectionDateBetween(
                associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertThat(januaryEntries).hasSize(2);
        assertThat(januaryEntries)
                .extracting(TracedCollectionEntryEntity::getWeightKg)
                .containsExactlyInAnyOrder(new BigDecimal("5.00"), new BigDecimal("3.00"));
    }

    @Test
    void findByAssociationIdAndCollectionDateBetweenIsInclusiveOnBothBoundaryDates() {
        // CertificateService (Task 51+) sums a certificate's period via this
        // exact query -- an exclusive boundary here would silently drop
        // kilos collected on the certificate's own first or last day.
        UUID associationId = UUID.randomUUID();
        LocalDate periodStart = LocalDate.of(2026, 3, 1);
        LocalDate periodEnd = LocalDate.of(2026, 3, 31);
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, periodStart, new BigDecimal("1.00"), Instant.now()));
        repository.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, periodEnd, new BigDecimal("2.00"), Instant.now()));

        List<TracedCollectionEntryEntity> entries =
                repository.findByAssociationIdAndCollectionDateBetween(associationId, periodStart, periodEnd);

        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(TracedCollectionEntryEntity::getCollectionDate)
                .containsExactlyInAnyOrder(periodStart, periodEnd);
    }
}

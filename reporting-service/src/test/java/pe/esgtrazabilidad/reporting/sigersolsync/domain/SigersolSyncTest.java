package pe.esgtrazabilidad.reporting.sigersolsync.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SigersolSyncTest {

    private UUID associationId() {
        return UUID.randomUUID();
    }

    @Test
    void createsASigersolSyncWithAGeneratedIdAndDeclaredAtNow() {
        SigersolSync sigersolSync = SigersolSync.create(
                associationId(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("85.50"),
                new BigDecimal("1200.00"),
                "Declaración anual 2026");

        assertThat(sigersolSync.getId()).isNotNull();
        assertThat(sigersolSync.getDeclaredAt()).isNotNull();
        assertThat(sigersolSync.getHierarchyCompliancePercent()).isEqualByComparingTo("85.50");
    }

    @Test
    void rejectsANullAssociationId() {
        assertThatThrownBy(() -> SigersolSync.create(
                        null, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("50"), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAPeriodEndBeforePeriodStart() {
        assertThatThrownBy(() -> SigersolSync.create(
                        associationId(),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 1, 1),
                        new BigDecimal("50"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAComplianceMorePercentAboveOneHundred() {
        assertThatThrownBy(() -> SigersolSync.create(
                        associationId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        new BigDecimal("100.01"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsACompliancePercentOfExactlyZero() {
        SigersolSync sigersolSync = SigersolSync.create(
                associationId(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), BigDecimal.ZERO, null, null);

        assertThat(sigersolSync.getHierarchyCompliancePercent()).isEqualByComparingTo("0");
    }

    @Test
    void acceptsACompliancePercentOfExactlyOneHundred() {
        SigersolSync sigersolSync = SigersolSync.create(
                associationId(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                BigDecimal.valueOf(100),
                null,
                null);

        assertThat(sigersolSync.getHierarchyCompliancePercent()).isEqualByComparingTo("100");
    }

    @Test
    void acceptsASingleDayPeriodWherePeriodEndEqualsPeriodStart() {
        LocalDate singleDay = LocalDate.of(2026, 1, 15);

        SigersolSync sigersolSync =
                SigersolSync.create(associationId(), singleDay, singleDay, new BigDecimal("50"), null, null);

        assertThat(sigersolSync.getPeriodStart()).isEqualTo(singleDay);
        assertThat(sigersolSync.getPeriodEnd()).isEqualTo(singleDay);
    }

    @Test
    void rejectsANegativeCompliancePercent() {
        assertThatThrownBy(() -> SigersolSync.create(
                        associationId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        new BigDecimal("-0.01"),
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeOfficialKilosDeclared() {
        assertThatThrownBy(() -> SigersolSync.create(
                        associationId(),
                        LocalDate.of(2026, 1, 1),
                        LocalDate.of(2026, 1, 31),
                        new BigDecimal("50"),
                        new BigDecimal("-0.01"),
                        null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingSigersolSyncWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();
        UUID associationId = associationId();

        SigersolSync sigersolSync = SigersolSync.reconstruct(
                id,
                associationId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("70.00"),
                null,
                java.time.Instant.parse("2026-02-01T00:00:00Z"),
                null);

        assertThat(sigersolSync.getId()).isEqualTo(id);
        assertThat(sigersolSync.getAssociationId()).isEqualTo(associationId);
    }
}

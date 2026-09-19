package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record RegisterSigersolSyncRequest(
        @NotNull UUID associationId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal hierarchyCompliancePercent,
        @PositiveOrZero BigDecimal officialKilosDeclared,
        String sourceNote) {
}

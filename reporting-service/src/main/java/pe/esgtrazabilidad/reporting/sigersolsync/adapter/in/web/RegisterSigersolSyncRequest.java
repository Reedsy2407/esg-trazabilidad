package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/**
 * {@code origin} is required on new records (D1): how the operator got the figure. {@code reference}
 * is an optional pointer to their proof. Who registers it comes from the token, never from here.
 */
public record RegisterSigersolSyncRequest(
        @NotNull UUID associationId,
        @NotNull LocalDate periodStart,
        @NotNull LocalDate periodEnd,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal hierarchyCompliancePercent,
        @PositiveOrZero BigDecimal officialKilosDeclared,
        String sourceNote,
        @NotNull SigersolOrigin origin,
        @Size(max = 255) String reference) {
}

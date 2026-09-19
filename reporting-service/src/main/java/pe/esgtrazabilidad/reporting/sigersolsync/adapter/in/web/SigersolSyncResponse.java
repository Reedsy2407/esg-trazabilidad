package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record SigersolSyncResponse(
        UUID id,
        UUID associationId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal hierarchyCompliancePercent,
        BigDecimal officialKilosDeclared,
        Instant declaredAt,
        String sourceNote) {
}

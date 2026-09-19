package pe.esgtrazabilidad.reporting.sigersolsync.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RegisterSigersolSyncCommand(
        UUID associationId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal hierarchyCompliancePercent,
        BigDecimal officialKilosDeclared,
        String sourceNote) {
}

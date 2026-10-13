package pe.esgtrazabilidad.reporting.sigersolsync.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/** {@code actor} comes from the JWT (never from the request body). */
public record RegisterSigersolSyncCommand(
        UUID associationId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal hierarchyCompliancePercent,
        BigDecimal officialKilosDeclared,
        String sourceNote,
        SigersolOrigin origin,
        String reference,
        CurrentActor actor) {
}

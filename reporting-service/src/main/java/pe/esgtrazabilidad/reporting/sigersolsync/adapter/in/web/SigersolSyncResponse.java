package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/** {@code origin} and the author are null for records made before v3 ("origen no registrado"). */
public record SigersolSyncResponse(
        UUID id,
        UUID associationId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal hierarchyCompliancePercent,
        BigDecimal officialKilosDeclared,
        Instant declaredAt,
        String sourceNote,
        SigersolOrigin origin,
        UUID registeredById,
        String registeredByEmail,
        String reference) {
}

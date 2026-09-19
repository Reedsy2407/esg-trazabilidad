package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record EsgCertificateResponse(
        UUID id,
        UUID trackedCompanyId,
        UUID associationId,
        String companyName,
        String companyRuc,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal kilosTrazados,
        BigDecimal hierarchyCompliancePercent,
        Instant issuedAt) {
}

package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CertificateSummaryResponse(
        UUID trackedCompanyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal kilosTrazados,
        BigDecimal hierarchyCompliancePercent) {
}

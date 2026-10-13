package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolCoverage;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/**
 * {@code sigersolCoverage} is UNVERIFIED ("No verificado") when no SIGERSOL record covers the period;
 * then the compliance figure and the origin are null. The origin is null too for a covering record
 * made before v3 ("origen no registrado").
 */
public record CertificateSummaryResponse(
        UUID trackedCompanyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal kilosTrazados,
        BigDecimal hierarchyCompliancePercent,
        SigersolCoverage sigersolCoverage,
        SigersolOrigin sigersolOrigin,
        String sigersolRegisteredByEmail,
        List<DistrictLine> districts) {
}

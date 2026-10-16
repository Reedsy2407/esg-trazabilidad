package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;

/**
 * {@code districtContext} is the SIGERSOL Municipal context of each district, frozen when the
 * certificate was issued (aggregated public counts with their year, never a claim about the
 * company). It is null in a list page: open the certificate to read it.
 *
 * <p>{@code hierarchyOrigin}, {@code hierarchyRegisteredByEmail} and {@code hierarchyReference}
 * say where the declared hierarchy-compliance figure came from (how the operator got it, who
 * registered it and the proof it points to). They are null in a list page and, in a single
 * certificate, for a figure registered before origins were kept (and the reference when none was given).
 */
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
        Instant issuedAt,
        EsgCertificateStatus status,
        Instant annulledAt,
        UUID annulledById,
        String annulledByEmail,
        String annulmentReason,
        List<DistrictLine> districtContext,
        SigersolOrigin hierarchyOrigin,
        String hierarchyRegisteredByEmail,
        String hierarchyReference) {
}

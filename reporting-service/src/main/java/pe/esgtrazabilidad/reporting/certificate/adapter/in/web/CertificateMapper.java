package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateCommand;

@Component
class CertificateMapper {

    CertificateSummaryResponse toResponse(CertificateSummary summary) {
        return new CertificateSummaryResponse(
                summary.trackedCompanyId(),
                summary.periodStart(),
                summary.periodEnd(),
                summary.kilosTrazados(),
                summary.hierarchyCompliancePercent(),
                summary.sigersolCoverage(),
                summary.sigersolOrigin(),
                summary.sigersolRegisteredByEmail(),
                summary.districts());
    }

    IssueCertificateCommand toCommand(UUID trackedCompanyId, IssueCertificateRequest request) {
        return new IssueCertificateCommand(trackedCompanyId, request.periodStart(), request.periodEnd());
    }

    /** For a list page: the district context is read on the single certificate. */
    EsgCertificateResponse toResponse(EsgCertificate certificate) {
        return toResponse(certificate, null);
    }

    EsgCertificateResponse toResponse(EsgCertificate certificate, List<DistrictLine> districtContext) {
        return new EsgCertificateResponse(
                certificate.getId(),
                certificate.getTrackedCompanyId(),
                certificate.getAssociationId(),
                certificate.getCompanyName(),
                certificate.getCompanyRuc(),
                certificate.getPeriodStart(),
                certificate.getPeriodEnd(),
                certificate.getKilosTrazados(),
                certificate.getHierarchyCompliancePercent(),
                certificate.getIssuedAt(),
                certificate.getStatus(),
                certificate.getAnnulledAt(),
                certificate.getAnnulledById(),
                certificate.getAnnulledByEmail(),
                certificate.getAnnulmentReason(),
                districtContext);
    }
}

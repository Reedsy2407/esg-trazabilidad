package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;

@Component
class CertificateMapper {

    CertificateSummaryResponse toResponse(CertificateSummary summary) {
        return new CertificateSummaryResponse(
                summary.trackedCompanyId(),
                summary.periodStart(),
                summary.periodEnd(),
                summary.kilosTrazados(),
                summary.hierarchyCompliancePercent());
    }
}

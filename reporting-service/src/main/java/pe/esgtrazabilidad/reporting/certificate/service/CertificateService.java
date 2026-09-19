package pe.esgtrazabilidad.reporting.certificate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.certificate.port.in.PreviewCertificateSummaryUseCase;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Service
class CertificateService implements PreviewCertificateSummaryUseCase {

    private final TrackedCompanyRepository trackedCompanyRepository;
    private final TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;
    private final SigersolSyncRepository sigersolSyncRepository;

    CertificateService(
            TrackedCompanyRepository trackedCompanyRepository,
            TracedCollectionEntryJpaRepository tracedCollectionEntryRepository,
            SigersolSyncRepository sigersolSyncRepository) {
        this.trackedCompanyRepository = trackedCompanyRepository;
        this.tracedCollectionEntryRepository = tracedCollectionEntryRepository;
        this.sigersolSyncRepository = sigersolSyncRepository;
    }

    @Override
    public CertificateSummary previewSummary(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd) {
        TrackedCompany trackedCompany = trackedCompanyRepository
                .findById(trackedCompanyId)
                .orElseThrow(() -> new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));

        BigDecimal kilosTrazados = tracedCollectionEntryRepository
                .findByAssociationIdAndCollectionDateBetween(trackedCompany.getAssociationId(), periodStart, periodEnd)
                .stream()
                .map(entry -> entry.getWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal hierarchyCompliancePercent = sigersolSyncRepository
                .findCovering(trackedCompany.getAssociationId(), periodStart, periodEnd)
                .map(SigersolSync::getHierarchyCompliancePercent)
                .orElse(null);

        return new CertificateSummary(trackedCompanyId, periodStart, periodEnd, kilosTrazados, hierarchyCompliancePercent);
    }
}

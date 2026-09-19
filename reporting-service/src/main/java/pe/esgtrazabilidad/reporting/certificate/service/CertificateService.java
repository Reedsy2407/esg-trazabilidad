package pe.esgtrazabilidad.reporting.certificate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.port.in.GetCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateCommand;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.ListCertificatesUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.PreviewCertificateSummaryUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Service
class CertificateService
        implements PreviewCertificateSummaryUseCase, IssueCertificateUseCase, GetCertificateUseCase, ListCertificatesUseCase {

    private final TrackedCompanyRepository trackedCompanyRepository;
    private final TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;
    private final SigersolSyncRepository sigersolSyncRepository;
    private final EsgCertificateRepository esgCertificateRepository;

    CertificateService(
            TrackedCompanyRepository trackedCompanyRepository,
            TracedCollectionEntryJpaRepository tracedCollectionEntryRepository,
            SigersolSyncRepository sigersolSyncRepository,
            EsgCertificateRepository esgCertificateRepository) {
        this.trackedCompanyRepository = trackedCompanyRepository;
        this.tracedCollectionEntryRepository = tracedCollectionEntryRepository;
        this.sigersolSyncRepository = sigersolSyncRepository;
        this.esgCertificateRepository = esgCertificateRepository;
    }

    @Override
    public CertificateSummary previewSummary(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd) {
        TrackedCompany trackedCompany = findTrackedCompany(trackedCompanyId);

        BigDecimal kilosTrazados = sumTracedKilos(trackedCompany.getAssociationId(), periodStart, periodEnd);

        BigDecimal hierarchyCompliancePercent = sigersolSyncRepository
                .findCovering(trackedCompany.getAssociationId(), periodStart, periodEnd)
                .map(SigersolSync::getHierarchyCompliancePercent)
                .orElse(null);

        return new CertificateSummary(trackedCompanyId, periodStart, periodEnd, kilosTrazados, hierarchyCompliancePercent);
    }

    @Override
    @Transactional
    public EsgCertificate issue(IssueCertificateCommand command) {
        TrackedCompany trackedCompany = findTrackedCompany(command.trackedCompanyId());

        // Service-level fast check first (clean 409 without ever reaching
        // the database's own rejection) -- the EXCLUDE USING gist
        // constraint on esg_certificate (Task 50) is the real backstop that
        // closes the race a plain check-then-act can't, to be proven with a
        // real two-thread test in Task 53.
        if (esgCertificateRepository.existsOverlapping(command.trackedCompanyId(), command.periodStart(), command.periodEnd())) {
            throw new ApplicationException(ReportingErrors.OVERLAPPING_CERTIFICATE_PERIOD);
        }

        SigersolSync sigersolSync = sigersolSyncRepository
                .findCovering(trackedCompany.getAssociationId(), command.periodStart(), command.periodEnd())
                .orElseThrow(() -> new ApplicationException(ReportingErrors.MISSING_SIGERSOL_DATA_FOR_PERIOD));

        List<TracedCollectionEntryEntity> entries = tracedCollectionEntryRepository
                .findByAssociationIdAndCollectionDateBetween(
                        trackedCompany.getAssociationId(), command.periodStart(), command.periodEnd());
        BigDecimal kilosTrazados =
                entries.stream().map(TracedCollectionEntryEntity::getWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add);

        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                command.periodStart(),
                command.periodEnd(),
                kilosTrazados,
                sigersolSync.getHierarchyCompliancePercent());
        List<EsgCertificateLineItem> lineItems = entries.stream()
                .map(entry -> EsgCertificateLineItem.of(certificate.getId(), entry.getCollectionDate(), entry.getWeightKg()))
                .toList();

        return esgCertificateRepository.save(certificate, lineItems);
    }

    @Override
    public EsgCertificate getById(UUID trackedCompanyId, UUID id) {
        EsgCertificate certificate = esgCertificateRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(ReportingErrors.CERTIFICATE_NOT_FOUND));
        if (!certificate.getTrackedCompanyId().equals(trackedCompanyId)) {
            // Same "wrong parent in the path" handling as
            // CollectionScheduleService.findScoped: a certificate that
            // exists but belongs to a DIFFERENT tracked company is reported
            // identically to one that doesn't exist at all -- never leaks
            // that the id is valid for someone else's certificate.
            throw new ApplicationException(ReportingErrors.CERTIFICATE_NOT_FOUND);
        }
        return certificate;
    }

    @Override
    public Page<EsgCertificate> list(UUID trackedCompanyId, Pageable pageable) {
        return esgCertificateRepository.findAll(trackedCompanyId, pageable);
    }

    private TrackedCompany findTrackedCompany(UUID trackedCompanyId) {
        return trackedCompanyRepository
                .findById(trackedCompanyId)
                .orElseThrow(() -> new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    private BigDecimal sumTracedKilos(UUID associationId, LocalDate periodStart, LocalDate periodEnd) {
        return tracedCollectionEntryRepository
                .findByAssociationIdAndCollectionDateBetween(associationId, periodStart, periodEnd)
                .stream()
                .map(TracedCollectionEntryEntity::getWeightKg)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

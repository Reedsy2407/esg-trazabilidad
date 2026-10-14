package pe.esgtrazabilidad.reporting.certificate.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.export.CertificateCsvExporter;
import pe.esgtrazabilidad.reporting.certificate.export.CertificatePdfExporter;
import pe.esgtrazabilidad.reporting.certificate.port.in.AnnulCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.ExportCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.GetCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateCommand;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.ListCertificatesUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.PreviewCertificateSummaryUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.service.DistrictContextService;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolCoverage;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Service
class CertificateService
        implements PreviewCertificateSummaryUseCase,
                IssueCertificateUseCase,
                AnnulCertificateUseCase,
                GetCertificateUseCase,
                ListCertificatesUseCase,
                ExportCertificateUseCase {

    private final TrackedCompanyRepository trackedCompanyRepository;
    private final TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;
    private final SigersolSyncRepository sigersolSyncRepository;
    private final EsgCertificateRepository esgCertificateRepository;
    private final CertificatePdfExporter certificatePdfExporter;
    private final CertificateCsvExporter certificateCsvExporter;
    private final DistrictContextService districtContextService;
    private final TransactionTemplate transactionTemplate;

    CertificateService(
            TrackedCompanyRepository trackedCompanyRepository,
            TracedCollectionEntryJpaRepository tracedCollectionEntryRepository,
            SigersolSyncRepository sigersolSyncRepository,
            EsgCertificateRepository esgCertificateRepository,
            CertificatePdfExporter certificatePdfExporter,
            CertificateCsvExporter certificateCsvExporter,
            DistrictContextService districtContextService,
            TransactionTemplate transactionTemplate) {
        this.districtContextService = districtContextService;
        this.transactionTemplate = transactionTemplate;
        this.trackedCompanyRepository = trackedCompanyRepository;
        this.tracedCollectionEntryRepository = tracedCollectionEntryRepository;
        this.sigersolSyncRepository = sigersolSyncRepository;
        this.esgCertificateRepository = esgCertificateRepository;
        this.certificatePdfExporter = certificatePdfExporter;
        this.certificateCsvExporter = certificateCsvExporter;
    }

    @Override
    public CertificateSummary previewSummary(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd) {
        requireValidPeriod(periodStart, periodEnd);
        TrackedCompany trackedCompany = findTrackedCompany(trackedCompanyId);

        List<TracedCollectionEntryEntity> entries = tracedCollectionEntryRepository
                .findByAssociationIdAndCollectionDateBetween(trackedCompany.getAssociationId(), periodStart, periodEnd);
        BigDecimal kilosTrazados = sumKilos(entries);
        List<DistrictLine> districts = districtLines(entries);

        SigersolSync covering = sigersolSyncRepository
                .findCovering(trackedCompany.getAssociationId(), periodStart, periodEnd)
                .orElse(null);
        if (covering == null) {
            return new CertificateSummary(
                    trackedCompanyId,
                    periodStart,
                    periodEnd,
                    kilosTrazados,
                    null,
                    SigersolCoverage.UNVERIFIED,
                    null,
                    null,
                    districts);
        }
        return new CertificateSummary(
                trackedCompanyId,
                periodStart,
                periodEnd,
                kilosTrazados,
                covering.getHierarchyCompliancePercent(),
                SigersolCoverage.RECORDED,
                covering.getOrigin(),
                covering.getRegisteredByEmail(),
                districts);
    }

    /**
     * Not transactional as a whole: the district context may call SIGERSOL over HTTP, and that must
     * not hold a database transaction open. Everything is read first, the context is computed, and
     * only the save runs in a transaction; the exclusion constraint on esg_certificate stays the
     * backstop for two overlapping issues racing.
     */
    @Override
    public EsgCertificate issue(IssueCertificateCommand command) {
        requireValidPeriod(command.periodStart(), command.periodEnd());
        TrackedCompany trackedCompany = findTrackedCompany(command.trackedCompanyId());
        if (trackedCompany.getStatus() == TrackedCompanyStatus.INACTIVE) {
            throw new ApplicationException(ReportingErrors.INACTIVE_TRACKED_COMPANY);
        }
        if (!trackedCompany.isVerified()) {
            throw new ApplicationException(ReportingErrors.UNVERIFIED_TRACKED_COMPANY);
        }

        // Service-level fast check first (clean 409 without ever reaching
        // the database's own rejection) -- the EXCLUDE USING gist
        // constraint on esg_certificate (Task 50) is the real backstop that
        // closes the race a plain check-then-act can't (proven by
        // CertificateConcurrencyApiIT).
        if (esgCertificateRepository.existsOverlapping(command.trackedCompanyId(), command.periodStart(), command.periodEnd())) {
            throw new ApplicationException(ReportingErrors.OVERLAPPING_CERTIFICATE_PERIOD);
        }

        SigersolSync sigersolSync = sigersolSyncRepository
                .findCovering(trackedCompany.getAssociationId(), command.periodStart(), command.periodEnd())
                .orElseThrow(() -> new ApplicationException(ReportingErrors.MISSING_SIGERSOL_DATA_FOR_PERIOD));

        List<TracedCollectionEntryEntity> entries = tracedCollectionEntryRepository
                .findByAssociationIdAndCollectionDateBetween(
                        trackedCompany.getAssociationId(), command.periodStart(), command.periodEnd());
        BigDecimal kilosTrazados = sumKilos(entries);

        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                command.periodStart(),
                command.periodEnd(),
                kilosTrazados,
                sigersolSync.getHierarchyCompliancePercent());
        List<EsgCertificateLineItem> lineItems = entries.stream()
                .map(entry -> EsgCertificateLineItem.of(
                        certificate.getId(), entry.getCollectionDate(), entry.getWeightKg(), entry.getDistrictUbigeo()))
                .toList();

        // The SIGERSOL context of each district is frozen with the certificate; it never blocks it
        // (a district SIGERSOL can't answer for is stored as "no disponible").
        List<DistrictLine> districts = districtLines(entries);
        return transactionTemplate.execute(status -> esgCertificateRepository.save(certificate, lineItems, districts));
    }

    @Override
    @Transactional
    public EsgCertificate annul(UUID trackedCompanyId, UUID id, String reason, CurrentActor actor) {
        EsgCertificate certificate = getById(trackedCompanyId, id);
        if (certificate.isAnnulled()) {
            throw new ApplicationException(ReportingErrors.CERTIFICATE_ALREADY_ANNULLED);
        }
        // The read above is only the fast path; the conditional UPDATE decides
        // when two annulments race.
        if (!esgCertificateRepository.annul(id, reason.strip(), actor, Instant.now())) {
            throw new ApplicationException(ReportingErrors.CERTIFICATE_ALREADY_ANNULLED);
        }
        return getById(trackedCompanyId, id);
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

    @Override
    public byte[] exportPdf(UUID trackedCompanyId, UUID id) {
        EsgCertificate certificate = getById(trackedCompanyId, id);
        // Frozen at issue: exporting never asks SIGERSOL again.
        return certificatePdfExporter.export(certificate, esgCertificateRepository.findDistrictLines(certificate.getId()));
    }

    @Override
    public byte[] exportCsv(UUID trackedCompanyId, UUID id) {
        EsgCertificate certificate = getById(trackedCompanyId, id);
        List<EsgCertificateLineItem> lineItems = esgCertificateRepository.findLineItems(certificate.getId());
        return certificateCsvExporter.export(
                certificate, lineItems, esgCertificateRepository.findDistrictLines(certificate.getId()));
    }

    // Both dates are @NotNull / required params at the HTTP edge; only their order is checked here.
    private static void requireValidPeriod(LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new ApplicationException(ReportingErrors.INVALID_CERTIFICATE_PERIOD);
        }
    }

    private TrackedCompany findTrackedCompany(UUID trackedCompanyId) {
        return trackedCompanyRepository
                .findById(trackedCompanyId)
                .orElseThrow(() -> new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    private static BigDecimal sumKilos(List<TracedCollectionEntryEntity> entries) {
        return entries.stream().map(TracedCollectionEntryEntity::getWeightKg).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Kilos per district with what SIGERSOL Municipal says about each one: by district name, the
     * kilos with no district last. A district's context is one snapshot read, so a certificate
     * with many districts does not mean many remote calls.
     */
    private List<DistrictLine> districtLines(List<TracedCollectionEntryEntity> entries) {
        Map<String, BigDecimal> kilosByDistrict = new LinkedHashMap<>();
        BigDecimal unknownKilos = BigDecimal.ZERO;
        boolean anyUnknown = false;
        for (TracedCollectionEntryEntity entry : entries) {
            if (entry.getDistrictUbigeo() == null) {
                unknownKilos = unknownKilos.add(entry.getWeightKg());
                anyUnknown = true;
            } else {
                kilosByDistrict.merge(entry.getDistrictUbigeo(), entry.getWeightKg(), BigDecimal::add);
            }
        }
        List<DistrictLine> lines = new ArrayList<>();
        kilosByDistrict.forEach((ubigeo, kilos) ->
                lines.add(DistrictLine.of(ubigeo, kilos, districtContextService.contextFor(ubigeo))));
        lines.sort(Comparator.comparing(DistrictLine::districtName));
        if (anyUnknown) {
            lines.add(DistrictLine.unknown(unknownKilos));
        }
        return lines;
    }

    @Override
    public List<DistrictLine> districtLines(UUID trackedCompanyId, UUID id) {
        return esgCertificateRepository.findDistrictLines(getById(trackedCompanyId, id).getId());
    }
}

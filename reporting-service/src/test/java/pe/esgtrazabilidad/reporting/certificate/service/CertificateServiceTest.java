package pe.esgtrazabilidad.reporting.certificate.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateCommand;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private TrackedCompanyRepository trackedCompanyRepository;

    @Mock
    private TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;

    @Mock
    private SigersolSyncRepository sigersolSyncRepository;

    @Mock
    private EsgCertificateRepository esgCertificateRepository;

    @Captor
    private ArgumentCaptor<List<EsgCertificateLineItem>> lineItemsCaptor;

    private CertificateService service;

    private final UUID trackedCompanyId = UUID.randomUUID();
    private final UUID associationId = UUID.randomUUID();
    private final LocalDate periodStart = LocalDate.of(2026, 1, 1);
    private final LocalDate periodEnd = LocalDate.of(2026, 1, 31);

    @BeforeEach
    void setUp() {
        service = new CertificateService(
                trackedCompanyRepository, tracedCollectionEntryRepository, sigersolSyncRepository, esgCertificateRepository);
    }

    private TrackedCompany sampleTrackedCompany() {
        return TrackedCompany.reconstruct(
                trackedCompanyId, "Empresa", "20123456789", associationId, TrackedCompanyStatus.ACTIVE);
    }

    // --- previewSummary (unchanged behavior, re-tested against the now-4-arg constructor) ---

    @Test
    void previewSummarySumsTracedKilosAndIncludesTheCoveringCompliancePercent() {
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(sampleTrackedCompany()));
        when(tracedCollectionEntryRepository.findByAssociationIdAndCollectionDateBetween(
                        associationId, periodStart, periodEnd))
                .thenReturn(List.of(
                        new TracedCollectionEntryEntity(
                                UUID.randomUUID(), associationId, periodStart, new BigDecimal("5.00"), null),
                        new TracedCollectionEntryEntity(
                                UUID.randomUUID(), associationId, periodEnd, new BigDecimal("3.00"), null)));
        SigersolSync sigersolSync =
                SigersolSync.create(associationId, periodStart, periodEnd, new BigDecimal("90.00"), null, null);
        when(sigersolSyncRepository.findCovering(associationId, periodStart, periodEnd))
                .thenReturn(Optional.of(sigersolSync));

        CertificateSummary summary = service.previewSummary(trackedCompanyId, periodStart, periodEnd);

        assertThat(summary.kilosTrazados()).isEqualByComparingTo("8.00");
        assertThat(summary.hierarchyCompliancePercent()).isEqualByComparingTo("90.00");
    }

    @Test
    void throwsNotFoundWhenTrackedCompanyDoesNotExistForPreview() {
        UUID missingId = UUID.randomUUID();
        when(trackedCompanyRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewSummary(missingId, periodStart, periodEnd))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    // --- issue() ---

    private IssueCertificateCommand sampleCommand() {
        return new IssueCertificateCommand(trackedCompanyId, periodStart, periodEnd);
    }

    @Test
    void issuesACertificateFreezingTheComputedKilosAndCompliancePercent() {
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(sampleTrackedCompany()));
        when(esgCertificateRepository.existsOverlapping(trackedCompanyId, periodStart, periodEnd)).thenReturn(false);
        SigersolSync sigersolSync =
                SigersolSync.create(associationId, periodStart, periodEnd, new BigDecimal("75.00"), null, null);
        when(sigersolSyncRepository.findCovering(associationId, periodStart, periodEnd))
                .thenReturn(Optional.of(sigersolSync));
        when(tracedCollectionEntryRepository.findByAssociationIdAndCollectionDateBetween(
                        associationId, periodStart, periodEnd))
                .thenReturn(List.of(new TracedCollectionEntryEntity(
                        UUID.randomUUID(), associationId, periodStart, new BigDecimal("12.00"), Instant.now())));
        when(esgCertificateRepository.save(any(EsgCertificate.class), any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EsgCertificate result = service.issue(sampleCommand());

        assertThat(result.getKilosTrazados()).isEqualByComparingTo("12.00");
        assertThat(result.getHierarchyCompliancePercent()).isEqualByComparingTo("75.00");
        verify(esgCertificateRepository).save(any(EsgCertificate.class), lineItemsCaptor.capture());
        assertThat(lineItemsCaptor.getValue()).hasSize(1);
        assertThat(lineItemsCaptor.getValue().get(0).getWeightKg()).isEqualByComparingTo("12.00");
    }

    @Test
    void rejectsIssuanceWhenAnOverlappingCertificateAlreadyExists() {
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(sampleTrackedCompany()));
        when(esgCertificateRepository.existsOverlapping(trackedCompanyId, periodStart, periodEnd)).thenReturn(true);

        assertThatThrownBy(() -> service.issue(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.OVERLAPPING_CERTIFICATE_PERIOD));
        verify(esgCertificateRepository, never()).save(any(), any());
        verify(sigersolSyncRepository, never()).findCovering(any(), any(), any());
    }

    @Test
    void rejectsIssuanceWhenNoSigersolDataCoversThePeriod() {
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(sampleTrackedCompany()));
        when(esgCertificateRepository.existsOverlapping(trackedCompanyId, periodStart, periodEnd)).thenReturn(false);
        when(sigersolSyncRepository.findCovering(associationId, periodStart, periodEnd)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.MISSING_SIGERSOL_DATA_FOR_PERIOD));
        verify(esgCertificateRepository, never()).save(any(), any());
    }

    @Test
    void throwsNotFoundWhenTrackedCompanyDoesNotExistForIssuance() {
        UUID missingId = UUID.randomUUID();
        when(trackedCompanyRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.issue(new IssueCertificateCommand(missingId, periodStart, periodEnd)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    // --- getById() ---

    @Test
    void returnsTheCertificateWhenItBelongsToTheGivenTrackedCompany() {
        EsgCertificate certificate = EsgCertificate.issue(
                sampleTrackedCompany(), periodStart, periodEnd, BigDecimal.TEN, BigDecimal.TEN);
        when(esgCertificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));

        EsgCertificate result = service.getById(trackedCompanyId, certificate.getId());

        assertThat(result).isEqualTo(certificate);
    }

    @Test
    void throwsNotFoundWhenTheCertificateBelongsToADifferentTrackedCompany() {
        // The exact "wrong parent in the path" case CollectionScheduleService's
        // own findScoped guards against: a certificate that exists but for a
        // DIFFERENT company must be reported identically to a missing one.
        EsgCertificate certificate = EsgCertificate.issue(
                sampleTrackedCompany(), periodStart, periodEnd, BigDecimal.TEN, BigDecimal.TEN);
        when(esgCertificateRepository.findById(certificate.getId())).thenReturn(Optional.of(certificate));
        UUID differentCompanyId = UUID.randomUUID();

        assertThatThrownBy(() -> service.getById(differentCompanyId, certificate.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.CERTIFICATE_NOT_FOUND));
    }

    @Test
    void throwsNotFoundWhenTheCertificateDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(esgCertificateRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(trackedCompanyId, missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.CERTIFICATE_NOT_FOUND));
    }

    // --- list() ---

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<EsgCertificate> expectedPage = new PageImpl<>(List.of());
        when(esgCertificateRepository.findAll(trackedCompanyId, pageable)).thenReturn(expectedPage);

        Page<EsgCertificate> result = service.list(trackedCompanyId, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

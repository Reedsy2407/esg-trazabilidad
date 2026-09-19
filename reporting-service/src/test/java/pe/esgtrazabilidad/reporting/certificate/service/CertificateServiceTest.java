package pe.esgtrazabilidad.reporting.certificate.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificateServiceTest {

    @Mock
    private TrackedCompanyRepository trackedCompanyRepository;

    @Mock
    private TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;

    @Mock
    private SigersolSyncRepository sigersolSyncRepository;

    private CertificateService service;

    @BeforeEach
    void setUp() {
        service = new CertificateService(trackedCompanyRepository, tracedCollectionEntryRepository, sigersolSyncRepository);
    }

    @Test
    void previewSummarySumsTracedKilosAndIncludesTheCoveringCompliancePercent() {
        UUID trackedCompanyId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        LocalDate periodStart = LocalDate.of(2026, 1, 1);
        LocalDate periodEnd = LocalDate.of(2026, 1, 31);
        TrackedCompany trackedCompany = TrackedCompany.reconstruct(
                trackedCompanyId,
                "Empresa",
                "20123456789",
                associationId,
                pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus.ACTIVE);
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(trackedCompany));
        when(tracedCollectionEntryRepository.findByAssociationIdAndCollectionDateBetween(
                        associationId, periodStart, periodEnd))
                .thenReturn(List.of(
                        new TracedCollectionEntryEntity(
                                UUID.randomUUID(), associationId, periodStart, new BigDecimal("5.00"), null),
                        new TracedCollectionEntryEntity(
                                UUID.randomUUID(), associationId, periodEnd, new BigDecimal("3.00"), null)));
        SigersolSync sigersolSync = SigersolSync.create(
                associationId, periodStart, periodEnd, new BigDecimal("90.00"), null, null);
        when(sigersolSyncRepository.findCovering(associationId, periodStart, periodEnd))
                .thenReturn(Optional.of(sigersolSync));

        CertificateSummary summary = service.previewSummary(trackedCompanyId, periodStart, periodEnd);

        assertThat(summary.kilosTrazados()).isEqualByComparingTo("8.00");
        assertThat(summary.hierarchyCompliancePercent()).isEqualByComparingTo("90.00");
    }

    @Test
    void previewSummaryReturnsNullCompliancePercentWhenNoSigersolSyncCoversThePeriod() {
        UUID trackedCompanyId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        LocalDate periodStart = LocalDate.of(2026, 1, 1);
        LocalDate periodEnd = LocalDate.of(2026, 1, 31);
        TrackedCompany trackedCompany = TrackedCompany.reconstruct(
                trackedCompanyId,
                "Empresa",
                "20123456789",
                associationId,
                pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus.ACTIVE);
        when(trackedCompanyRepository.findById(trackedCompanyId)).thenReturn(Optional.of(trackedCompany));
        when(tracedCollectionEntryRepository.findByAssociationIdAndCollectionDateBetween(
                        associationId, periodStart, periodEnd))
                .thenReturn(List.of());
        when(sigersolSyncRepository.findCovering(associationId, periodStart, periodEnd)).thenReturn(Optional.empty());

        CertificateSummary summary = service.previewSummary(trackedCompanyId, periodStart, periodEnd);

        assertThat(summary.kilosTrazados()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.hierarchyCompliancePercent()).isNull();
    }

    @Test
    void throwsNotFoundWhenTrackedCompanyDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(trackedCompanyRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.previewSummary(missingId, LocalDate.now(), LocalDate.now()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }
}

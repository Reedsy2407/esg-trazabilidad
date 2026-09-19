package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.certificate.CertificateSummary;

public interface PreviewCertificateSummaryUseCase {

    CertificateSummary previewSummary(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd);
}

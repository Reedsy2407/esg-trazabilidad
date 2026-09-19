package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.util.UUID;

public interface ExportCertificateUseCase {

    byte[] exportPdf(UUID trackedCompanyId, UUID id);

    byte[] exportCsv(UUID trackedCompanyId, UUID id);
}

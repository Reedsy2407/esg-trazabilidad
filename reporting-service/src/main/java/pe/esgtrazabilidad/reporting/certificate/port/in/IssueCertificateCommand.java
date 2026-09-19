package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.time.LocalDate;
import java.util.UUID;

public record IssueCertificateCommand(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd) {
}

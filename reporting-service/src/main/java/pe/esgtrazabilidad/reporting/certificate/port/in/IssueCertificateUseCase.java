package pe.esgtrazabilidad.reporting.certificate.port.in;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

public interface IssueCertificateUseCase {

    EsgCertificate issue(IssueCertificateCommand command);
}

package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

public interface GetCertificateUseCase {

    EsgCertificate getById(UUID trackedCompanyId, UUID id);
}

package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

public interface AnnulCertificateUseCase {

    /** RPT-003 if the certificate is not this company's, RPT-019 if it is already annulled. */
    EsgCertificate annul(UUID trackedCompanyId, UUID id, String reason, CurrentActor actor);
}

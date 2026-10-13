package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

public interface GetCertificateUseCase {

    EsgCertificate getById(UUID trackedCompanyId, UUID id);

    /** The SIGERSOL context of each district, as frozen when the certificate was issued. */
    List<DistrictLine> districtLines(UUID trackedCompanyId, UUID id);
}

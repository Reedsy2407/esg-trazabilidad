package pe.esgtrazabilidad.reporting.certificate.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

public interface ListCertificatesUseCase {

    Page<EsgCertificate> list(UUID trackedCompanyId, Pageable pageable);
}

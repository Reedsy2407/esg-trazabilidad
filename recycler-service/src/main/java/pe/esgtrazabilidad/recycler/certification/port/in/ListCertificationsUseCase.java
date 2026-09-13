package pe.esgtrazabilidad.recycler.certification.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface ListCertificationsUseCase {

    Page<Certification> list(UUID associationId, Pageable pageable);
}

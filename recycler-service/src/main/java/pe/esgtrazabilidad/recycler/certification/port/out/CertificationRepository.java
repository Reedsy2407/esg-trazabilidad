package pe.esgtrazabilidad.recycler.certification.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface CertificationRepository {

    Certification save(Certification certification);

    Certification update(Certification certification);

    Optional<Certification> findById(UUID id);

    Page<Certification> findAll(UUID associationId, Pageable pageable);
}

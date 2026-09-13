package pe.esgtrazabilidad.recycler.certification.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface CertificationRepository {

    Certification save(Certification certification);

    Optional<Certification> findById(UUID id);
}

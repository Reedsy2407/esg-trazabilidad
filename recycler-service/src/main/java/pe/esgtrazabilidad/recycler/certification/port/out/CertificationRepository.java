package pe.esgtrazabilidad.recycler.certification.port.out;

import java.util.List;
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

    /**
     * Used by CertificationExpiryScanJob (Task 33) -- mirrors
     * Certification.isExpired()'s own definition of "expired"
     * (expirationDate before today), plus "never notified".
     */
    List<Certification> findExpiredAndNotYetNotified();
}

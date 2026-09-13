package pe.esgtrazabilidad.recycler.certification.port.in;

import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface RenewCertificationUseCase {

    Certification renew(UUID associationId, UUID id, LocalDate newExpirationDate);
}

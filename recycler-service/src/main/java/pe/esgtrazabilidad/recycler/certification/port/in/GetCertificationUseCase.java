package pe.esgtrazabilidad.recycler.certification.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface GetCertificationUseCase {

    Certification getById(UUID associationId, UUID id);
}

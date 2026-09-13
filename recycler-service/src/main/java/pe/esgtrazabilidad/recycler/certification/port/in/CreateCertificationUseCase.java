package pe.esgtrazabilidad.recycler.certification.port.in;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

public interface CreateCertificationUseCase {

    Certification create(CreateCertificationCommand command);
}

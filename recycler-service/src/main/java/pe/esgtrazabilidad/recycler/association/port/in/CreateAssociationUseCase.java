package pe.esgtrazabilidad.recycler.association.port.in;

import pe.esgtrazabilidad.recycler.association.domain.Association;

public interface CreateAssociationUseCase {

    Association create(CreateAssociationCommand command);
}

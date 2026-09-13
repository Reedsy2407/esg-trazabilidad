package pe.esgtrazabilidad.recycler.association.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.recycler.association.domain.Association;

public interface ActivateAssociationUseCase {

    Association activate(UUID id);
}

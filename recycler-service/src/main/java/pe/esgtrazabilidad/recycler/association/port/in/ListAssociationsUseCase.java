package pe.esgtrazabilidad.recycler.association.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;

public interface ListAssociationsUseCase {

    Page<Association> list(AssociationStatus status, Pageable pageable);
}

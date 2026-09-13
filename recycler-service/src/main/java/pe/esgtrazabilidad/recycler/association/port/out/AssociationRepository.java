package pe.esgtrazabilidad.recycler.association.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.recycler.association.domain.Association;

public interface AssociationRepository {

    Association save(Association association);

    Optional<Association> findById(UUID id);

    Optional<Association> findByRuc(String ruc);
}

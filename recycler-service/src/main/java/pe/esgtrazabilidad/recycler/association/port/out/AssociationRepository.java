package pe.esgtrazabilidad.recycler.association.port.out;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;

public interface AssociationRepository {

    Association save(Association association);

    Association update(Association association);

    Optional<Association> findById(UUID id);

    Optional<Association> findByRuc(String ruc);

    Page<Association> findAll(AssociationStatus status, Pageable pageable);

    /**
     * Denormalized running total, touched only by CollectionRegisteredEventListener
     * (Task 30) -- a single atomic SQL UPDATE, never a load-mutate-save through
     * save()/update() above (those never see this field at all).
     */
    void incrementTotalKilos(UUID associationId, BigDecimal amount);
}

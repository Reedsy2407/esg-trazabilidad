package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface AssociationJpaRepository extends JpaRepository<AssociationEntity, UUID> {

    Optional<AssociationEntity> findByRuc(String ruc);
}

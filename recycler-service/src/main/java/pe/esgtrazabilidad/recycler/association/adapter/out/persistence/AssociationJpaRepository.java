package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface AssociationJpaRepository
        extends JpaRepository<AssociationEntity, UUID>, JpaSpecificationExecutor<AssociationEntity> {

    Optional<AssociationEntity> findByRuc(String ruc);
}

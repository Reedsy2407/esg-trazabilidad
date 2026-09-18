package pe.esgtrazabilidad.collection.association.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface BlockedAssociationJpaRepository extends JpaRepository<BlockedAssociationEntity, UUID> {
}

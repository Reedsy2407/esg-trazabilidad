package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface NeighborJpaRepository
        extends JpaRepository<NeighborEntity, UUID>, JpaSpecificationExecutor<NeighborEntity> {
}

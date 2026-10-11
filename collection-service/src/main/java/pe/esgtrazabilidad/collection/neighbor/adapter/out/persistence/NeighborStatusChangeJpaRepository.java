package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface NeighborStatusChangeJpaRepository extends JpaRepository<NeighborStatusChangeEntity, UUID> {

    List<NeighborStatusChangeEntity> findByNeighborIdOrderByChangedAtDescIdDesc(UUID neighborId);
}

package pe.esgtrazabilidad.collection.schedule.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CollectionScheduleJpaRepository
        extends JpaRepository<CollectionScheduleEntity, UUID>, JpaSpecificationExecutor<CollectionScheduleEntity> {
}

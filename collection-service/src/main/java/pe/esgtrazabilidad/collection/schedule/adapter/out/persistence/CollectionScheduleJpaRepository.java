package pe.esgtrazabilidad.collection.schedule.adapter.out.persistence;

import java.time.DayOfWeek;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CollectionScheduleJpaRepository
        extends JpaRepository<CollectionScheduleEntity, UUID>, JpaSpecificationExecutor<CollectionScheduleEntity> {

    Optional<CollectionScheduleEntity> findByNeighborIdAndDayOfWeekAndStatus(
            UUID neighborId, DayOfWeek dayOfWeek, String status);

    Page<CollectionScheduleEntity> findByNeighborId(UUID neighborId, Pageable pageable);
}

package pe.esgtrazabilidad.collection.schedule.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.domain.CollectionScheduleStatus;
import pe.esgtrazabilidad.collection.schedule.port.out.CollectionScheduleRepository;

@Component
class CollectionScheduleRepositoryAdapter implements CollectionScheduleRepository {

    private final CollectionScheduleJpaRepository jpaRepository;

    CollectionScheduleRepositoryAdapter(CollectionScheduleJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public CollectionSchedule save(CollectionSchedule schedule) {
        return toDomain(jpaRepository.save(toEntity(schedule)));
    }

    @Override
    public CollectionSchedule update(CollectionSchedule schedule) {
        CollectionScheduleEntity existing = CollectionScheduleEntity.existing(
                schedule.getId(),
                schedule.getNeighborId(),
                schedule.getDayOfWeek(),
                schedule.getTime(),
                schedule.getStatus().name());
        return toDomain(jpaRepository.save(existing));
    }

    @Override
    public Optional<CollectionSchedule> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    private CollectionScheduleEntity toEntity(CollectionSchedule schedule) {
        return new CollectionScheduleEntity(
                schedule.getId(),
                schedule.getNeighborId(),
                schedule.getDayOfWeek(),
                schedule.getTime(),
                schedule.getStatus().name());
    }

    private CollectionSchedule toDomain(CollectionScheduleEntity entity) {
        return CollectionSchedule.reconstruct(
                entity.getId(),
                entity.getNeighborId(),
                entity.getDayOfWeek(),
                entity.getTime(),
                CollectionScheduleStatus.valueOf(entity.getStatus()));
    }
}

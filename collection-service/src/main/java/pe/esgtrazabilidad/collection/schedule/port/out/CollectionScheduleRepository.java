package pe.esgtrazabilidad.collection.schedule.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface CollectionScheduleRepository {

    CollectionSchedule save(CollectionSchedule schedule);

    CollectionSchedule update(CollectionSchedule schedule);

    Optional<CollectionSchedule> findById(UUID id);
}

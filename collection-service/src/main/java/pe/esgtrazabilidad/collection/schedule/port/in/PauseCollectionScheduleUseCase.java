package pe.esgtrazabilidad.collection.schedule.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface PauseCollectionScheduleUseCase {

    CollectionSchedule pause(UUID neighborId, UUID id);
}

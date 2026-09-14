package pe.esgtrazabilidad.collection.schedule.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface CancelCollectionScheduleUseCase {

    CollectionSchedule cancel(UUID neighborId, UUID id);
}

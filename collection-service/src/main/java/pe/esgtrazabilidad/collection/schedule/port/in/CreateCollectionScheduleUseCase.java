package pe.esgtrazabilidad.collection.schedule.port.in;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;

public interface CreateCollectionScheduleUseCase {

    CollectionSchedule create(CreateCollectionScheduleCommand command);
}

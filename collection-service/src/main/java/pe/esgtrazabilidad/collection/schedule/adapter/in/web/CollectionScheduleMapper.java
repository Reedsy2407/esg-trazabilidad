package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleCommand;

@Component
class CollectionScheduleMapper {

    CreateCollectionScheduleCommand toCommand(UUID neighborId, CreateCollectionScheduleRequest request) {
        return new CreateCollectionScheduleCommand(neighborId, request.dayOfWeek(), request.time());
    }

    CollectionScheduleResponse toResponse(CollectionSchedule schedule) {
        return new CollectionScheduleResponse(
                schedule.getId(),
                schedule.getNeighborId(),
                schedule.getDayOfWeek(),
                schedule.getTime(),
                schedule.getStatus());
    }
}

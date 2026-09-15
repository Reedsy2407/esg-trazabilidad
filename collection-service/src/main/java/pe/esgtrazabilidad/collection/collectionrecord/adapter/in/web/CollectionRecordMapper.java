package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordCommand;

@Component
class CollectionRecordMapper {

    CreateCollectionRecordCommand toCommand(UUID neighborId, CreateCollectionRecordRequest request) {
        return new CreateCollectionRecordCommand(
                neighborId, request.scheduleId(), request.associationId(), request.collectionDate(), request.weightKg());
    }

    CollectionRecordResponse toResponse(CollectionRecord record) {
        return new CollectionRecordResponse(
                record.getId(),
                record.getNeighborId(),
                record.getScheduleId(),
                record.getAssociationId(),
                record.getCollectionDate(),
                record.getWeightKg());
    }
}

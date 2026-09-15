package pe.esgtrazabilidad.collection.collectionrecord.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;

public interface GetCollectionRecordUseCase {

    CollectionRecord getById(UUID neighborId, UUID id);
}

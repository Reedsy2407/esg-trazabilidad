package pe.esgtrazabilidad.collection.collectionrecord.port.in;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;

public interface CreateCollectionRecordUseCase {

    CollectionRecord create(CreateCollectionRecordCommand command);
}

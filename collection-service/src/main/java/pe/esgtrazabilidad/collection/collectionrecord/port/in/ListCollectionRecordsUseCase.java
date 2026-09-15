package pe.esgtrazabilidad.collection.collectionrecord.port.in;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;

public interface ListCollectionRecordsUseCase {

    Page<CollectionRecord> list(UUID neighborId, LocalDate from, LocalDate to, Pageable pageable);
}

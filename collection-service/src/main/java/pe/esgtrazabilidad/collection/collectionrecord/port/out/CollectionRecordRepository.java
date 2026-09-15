package pe.esgtrazabilidad.collection.collectionrecord.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;

public interface CollectionRecordRepository {

    CollectionRecord save(CollectionRecord record);

    Optional<CollectionRecord> findById(UUID id);

    Page<CollectionRecord> findAll(UUID neighborId, LocalDate from, LocalDate to, Pageable pageable);
}

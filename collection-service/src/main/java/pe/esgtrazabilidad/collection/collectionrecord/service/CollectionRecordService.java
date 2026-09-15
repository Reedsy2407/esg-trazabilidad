package pe.esgtrazabilidad.collection.collectionrecord.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordCommand;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.GetCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.ListCollectionRecordsUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.out.CollectionRecordRepository;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class CollectionRecordService
        implements CreateCollectionRecordUseCase, GetCollectionRecordUseCase, ListCollectionRecordsUseCase {

    private final CollectionRecordRepository repository;
    private final NeighborRepository neighborRepository;

    CollectionRecordService(CollectionRecordRepository repository, NeighborRepository neighborRepository) {
        this.repository = repository;
        this.neighborRepository = neighborRepository;
    }

    @Override
    public CollectionRecord create(CreateCollectionRecordCommand command) {
        neighborRepository
                .findById(command.neighborId())
                .orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
        // associationId is deliberately NOT validated here -- collection-service
        // depends only on shared-kernel, never recycler-service. Real validation
        // is deferred to cross-service-events (see SPEC-collection-service.md).
        CollectionRecord record = CollectionRecord.create(
                command.neighborId(),
                command.scheduleId(),
                command.associationId(),
                command.collectionDate(),
                command.weightKg());
        return repository.save(record);
    }

    @Override
    public CollectionRecord getById(UUID neighborId, UUID id) {
        CollectionRecord record = repository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(CollectionErrors.RECORD_NOT_FOUND));
        if (!record.getNeighborId().equals(neighborId)) {
            throw new ApplicationException(CollectionErrors.RECORD_NOT_FOUND);
        }
        return record;
    }

    @Override
    public Page<CollectionRecord> list(UUID neighborId, LocalDate from, LocalDate to, Pageable pageable) {
        return repository.findAll(neighborId, from, to, pageable);
    }
}

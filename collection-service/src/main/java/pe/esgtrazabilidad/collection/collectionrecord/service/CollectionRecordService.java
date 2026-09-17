package pe.esgtrazabilidad.collection.collectionrecord.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.events.CollectionRegisteredEvent;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordCommand;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.GetCollectionRecordUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.ListCollectionRecordsUseCase;
import pe.esgtrazabilidad.collection.collectionrecord.port.out.CollectionRecordRepository;
import pe.esgtrazabilidad.collection.events.publish.CollectionRegisteredEventPublisher;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.out.CollectionScheduleRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class CollectionRecordService
        implements CreateCollectionRecordUseCase, GetCollectionRecordUseCase, ListCollectionRecordsUseCase {

    private final CollectionRecordRepository repository;
    private final NeighborRepository neighborRepository;
    private final CollectionScheduleRepository scheduleRepository;
    private final CollectionRegisteredEventPublisher eventPublisher;

    CollectionRecordService(
            CollectionRecordRepository repository,
            NeighborRepository neighborRepository,
            CollectionScheduleRepository scheduleRepository,
            CollectionRegisteredEventPublisher eventPublisher) {
        this.repository = repository;
        this.neighborRepository = neighborRepository;
        this.scheduleRepository = scheduleRepository;
        this.eventPublisher = eventPublisher;
    }

    // The outbox write (inside eventPublisher.publish()) must land in the same
    // DB transaction as repository.save() -- neither call gets one on its own
    // otherwise, since each is a separate SimpleJpaRepository method.
    @Override
    @Transactional
    public CollectionRecord create(CreateCollectionRecordCommand command) {
        neighborRepository
                .findById(command.neighborId())
                .orElseThrow(() -> new ApplicationException(CollectionErrors.NEIGHBOR_NOT_FOUND));
        if (command.scheduleId() != null) {
            // The FK only guarantees the schedule exists somewhere, not that
            // it belongs to this neighbor -- checked explicitly here. Same
            // error code as "doesn't exist" (COL-006), so a mismatched
            // scheduleId doesn't leak that the schedule exists elsewhere.
            CollectionSchedule schedule = scheduleRepository
                    .findById(command.scheduleId())
                    .orElseThrow(() -> new ApplicationException(CollectionErrors.SCHEDULE_NOT_FOUND));
            if (!schedule.getNeighborId().equals(command.neighborId())) {
                throw new ApplicationException(CollectionErrors.SCHEDULE_NOT_FOUND);
            }
        }
        // associationId is deliberately NOT validated here -- collection-service
        // depends only on shared-kernel, never recycler-service. Real validation
        // is deferred to cross-service-events (see SPEC-collection-service.md).
        CollectionRecord record = CollectionRecord.create(
                command.neighborId(),
                command.scheduleId(),
                command.associationId(),
                command.collectionDate(),
                command.weightKg());
        CollectionRecord saved = repository.save(record);
        eventPublisher.publish(CollectionRegisteredEvent.from(saved));
        return saved;
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

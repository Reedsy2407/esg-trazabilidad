package pe.esgtrazabilidad.reporting.events.consume;

import java.time.Instant;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;

/**
 * No separate @Transactional processor bean here, unlike recycler-service's
 * own consumer (Task 30): that split exists there because incrementTotalKilos
 * and the ledger insert are two writes that must commit or roll back
 * together. Here, TracedCollectionEntryEntity's own row IS the entire write
 * -- it doubles as both the idempotency marker and the business record (see
 * SPEC-reporting-service.md's Resolved Decisions) -- so a single
 * repository.saveAndFlush() call is already atomic on its own via Spring
 * Data's default per-method transaction; wrapping it in an extra bean would
 * add ceremony with no corresponding correctness need.
 */
@Component
class CollectionRegisteredEventListener {

    private final TracedCollectionEntryJpaRepository repository;

    CollectionRegisteredEventListener(TracedCollectionEntryJpaRepository repository) {
        this.repository = repository;
    }

    @RabbitListener(
            queues = CollectionRegisteredQueueConfig.QUEUE_NAME,
            containerFactory = "jsonRabbitListenerContainerFactory")
    void handle(CollectionRegisteredEvent event) {
        try {
            repository.saveAndFlush(new TracedCollectionEntryEntity(
                    event.eventId(), event.associationId(), event.collectionDate(), event.weightKg(), Instant.now()));
        } catch (DataIntegrityViolationException exception) {
            // Already processed -- redelivery of the same eventId, ack and
            // move on, not an error.
        }
    }
}

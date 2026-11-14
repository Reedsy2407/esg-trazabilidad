package pe.esgtrazabilidad.auth.events.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

/** The outbox of auth-service: a FAILED row counts as pending, so a failed publication is retried. */
@Component
class OutboxEventRepositoryAdapter implements OutboxRepository {

    private final OutboxEventJpaRepository jpaRepository;

    OutboxEventRepositoryAdapter(OutboxEventJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(OutboxEntry entry) {
        jpaRepository.save(new OutboxEventEntity(
                entry.id(),
                entry.eventType(),
                entry.routingKey(),
                entry.payloadJson(),
                entry.status().name(),
                entry.createdAt()));
    }

    @Override
    public List<OutboxEntry> findPendingBatch(int limit) {
        return jpaRepository.findPending(PageRequest.of(0, limit)).stream().map(this::toDomain).toList();
    }

    @Override
    public void markProcessed(UUID id) {
        jpaRepository.updateStatus(id, OutboxStatus.PROCESSED.name());
    }

    @Override
    public void markFailed(UUID id) {
        jpaRepository.updateStatus(id, OutboxStatus.FAILED.name());
    }

    private OutboxEntry toDomain(OutboxEventEntity entity) {
        return new OutboxEntry(
                entity.getId(),
                entity.getEventType(),
                entity.getRoutingKey(),
                entity.getPayloadJson(),
                OutboxStatus.valueOf(entity.getStatus()),
                entity.getCreatedAt());
    }
}

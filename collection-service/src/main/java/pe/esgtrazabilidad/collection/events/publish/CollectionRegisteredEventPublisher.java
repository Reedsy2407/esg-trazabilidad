package pe.esgtrazabilidad.collection.events.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.collectionrecord.events.CollectionRegisteredEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

@Component
public class CollectionRegisteredEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    CollectionRegisteredEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(CollectionRegisteredEvent event) {
        // Reuses the event's own eventId/occurredAt as the outbox row's id/
        // createdAt -- one canonical identity for the event, not two unrelated
        // UUIDs (the outbox row's own PK and the wire-level event id a consumer's
        // idempotency ledger keys off).
        outboxRepository.save(new OutboxEntry(
                event.eventId(),
                CollectionRegisteredEvent.class.getSimpleName(),
                event.routingKey(),
                toJson(event),
                OutboxStatus.NEW,
                event.occurredAt()));
    }

    private String toJson(CollectionRegisteredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar " + CollectionRegisteredEvent.class.getSimpleName(), exception);
        }
    }
}

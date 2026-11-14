package pe.esgtrazabilidad.collection.events.publish;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.esgtrazabilidad.kernel.events.DomainEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

/**
 * Writes any domain event to the outbox, reusing the event's own {@code eventId} and
 * {@code occurredAt} as the row's id and creation time (one identity for the event). Call it
 * inside the transaction of the change the event announces: the outbox row commits or rolls
 * back with it. The dispatcher publishes it later and retries a failed one.
 */
@Component
public class DomainEventOutbox {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    DomainEventOutbox(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(DomainEvent event) {
        outboxRepository.save(new OutboxEntry(
                event.eventId(),
                event.getClass().getSimpleName(),
                event.routingKey(),
                toJson(event),
                OutboxStatus.NEW,
                event.occurredAt()));
    }

    private String toJson(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar " + event.getClass().getSimpleName(), exception);
        }
    }
}

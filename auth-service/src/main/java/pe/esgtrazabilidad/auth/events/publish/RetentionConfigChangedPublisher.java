package pe.esgtrazabilidad.auth.events.publish;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

/**
 * Writes the event to the outbox, reusing the event's own id and time as the row's. Call it inside the
 * transaction of the change it announces: the row commits or rolls back with it.
 */
@Component
public class RetentionConfigChangedPublisher {

    private final OutboxRepository outbox;
    private final ObjectMapper objectMapper;

    RetentionConfigChangedPublisher(OutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public void publish(RetentionConfigChangedEvent event) {
        try {
            outbox.save(new OutboxEntry(
                    event.eventId(),
                    RetentionConfigChangedEvent.class.getSimpleName(),
                    event.routingKey(),
                    objectMapper.writeValueAsString(event),
                    OutboxStatus.NEW,
                    event.occurredAt()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo serializar RetentionConfigChangedEvent", exception);
        }
    }
}

package pe.esgtrazabilidad.recycler.events.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;
import pe.esgtrazabilidad.recycler.recycler.events.RecyclerStatusChangedEvent;

@Component
public class RecyclerStatusChangedEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    RecyclerStatusChangedEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(RecyclerStatusChangedEvent event) {
        outboxRepository.save(new OutboxEntry(
                event.eventId(),
                RecyclerStatusChangedEvent.class.getSimpleName(),
                event.routingKey(),
                toJson(event),
                OutboxStatus.NEW,
                event.occurredAt()));
    }

    private String toJson(RecyclerStatusChangedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar " + RecyclerStatusChangedEvent.class.getSimpleName(), exception);
        }
    }
}

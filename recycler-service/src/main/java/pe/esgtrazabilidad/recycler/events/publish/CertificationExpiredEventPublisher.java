package pe.esgtrazabilidad.recycler.events.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.certification.events.CertificationExpiredEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

@Component
public class CertificationExpiredEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    CertificationExpiredEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(CertificationExpiredEvent event) {
        outboxRepository.save(new OutboxEntry(
                event.eventId(),
                CertificationExpiredEvent.class.getSimpleName(),
                event.routingKey(),
                toJson(event),
                OutboxStatus.NEW,
                event.occurredAt()));
    }

    private String toJson(CertificationExpiredEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar " + CertificationExpiredEvent.class.getSimpleName(), exception);
        }
    }
}

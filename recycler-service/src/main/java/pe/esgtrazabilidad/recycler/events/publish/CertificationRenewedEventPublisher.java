package pe.esgtrazabilidad.recycler.events.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.certification.events.CertificationRenewedEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

@Component
public class CertificationRenewedEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    CertificationRenewedEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publish(CertificationRenewedEvent event) {
        outboxRepository.save(new OutboxEntry(
                event.eventId(),
                CertificationRenewedEvent.class.getSimpleName(),
                event.routingKey(),
                toJson(event),
                OutboxStatus.NEW,
                event.occurredAt()));
    }

    private String toJson(CertificationRenewedEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "No se pudo serializar " + CertificationRenewedEvent.class.getSimpleName(), exception);
        }
    }
}

package pe.esgtrazabilidad.collection.events.consume;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/**
 * Feeds the local RecyclerStatusCopy. No ledger table: the copy itself decides.
 * An event applies only if its {@code occurredAt} is newer than the stored one, in a
 * single atomic statement, so a repeated eventId, an old event, or two events racing
 * for the same recycler all end in the correct state with no extra bookkeeping.
 */
@Component
class RecyclerStatusEventListener {

    private static final Logger log = LoggerFactory.getLogger(RecyclerStatusEventListener.class);

    private final RecyclerStatusCopyRepository repository;
    private final ObjectMapper objectMapper;

    RecyclerStatusEventListener(RecyclerStatusCopyRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RecyclerStatusQueueConfig.QUEUE_NAME)
    void handle(Message message) {
        RecyclerStatusChangedEvent event;
        try {
            event = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8), RecyclerStatusChangedEvent.class);
        } catch (JsonProcessingException exception) {
            // A malformed message can never become valid: drop it instead of redelivering forever.
            log.warn("event=recycler_status_discarded reason=unreadable_payload");
            return;
        }
        if (event.recyclerId() == null
                || event.associationId() == null
                || event.status() == null
                || event.occurredAt() == null) {
            log.warn("event=recycler_status_discarded reason=missing_fields");
            return;
        }
        repository.applyIfNewer(event.recyclerId(), event.associationId(), event.status(), event.occurredAt());
    }
}

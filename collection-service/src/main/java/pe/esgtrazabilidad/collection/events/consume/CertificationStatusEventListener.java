package pe.esgtrazabilidad.collection.events.consume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * One queue, one listener, two event types (see
 * SPEC-cross-service-events.md's Project Structure). This is deliberately
 * NOT a typed-record listener with {@code containerFactory =
 * "jsonRabbitListenerContainerFactory"} (Task 30/31's pattern): Spring
 * AMQP's Jackson2JsonMessageConverter picks the target Java type from the
 * listener METHOD's own declared parameter type (or a "__TypeId__" header
 * neither side ever sets, since the outbox publishes pre-serialized JSON
 * strings, not converter-serialized objects) -- with two possible shapes on
 * one queue, there's no single fixed type to hand it. Taking the raw
 * {@link Message} instead and dispatching on the routing key sidesteps that
 * limitation entirely; the default (Boot-auto-configured) container
 * factory is used since no automatic conversion is needed here.
 */
@Component
class CertificationStatusEventListener {

    private static final String EXPIRED_ROUTING_KEY = "certification.expired";
    private static final String RENEWED_ROUTING_KEY = "certification.renewed";

    private final CertificationStatusEventProcessor processor;
    private final ObjectMapper objectMapper;

    CertificationStatusEventListener(CertificationStatusEventProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = CertificationStatusQueueConfig.QUEUE_NAME)
    void handle(Message message, @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            switch (routingKey) {
                case EXPIRED_ROUTING_KEY ->
                        processor.processExpired(objectMapper.readValue(body, CertificationExpiredEvent.class));
                case RENEWED_ROUTING_KEY ->
                        processor.processRenewed(objectMapper.readValue(body, CertificationRenewedEvent.class));
                default -> {
                    // Not expected given this queue's own bindings, but
                    // ignored defensively rather than thrown -- never blocks
                    // the queue over a message this listener doesn't own.
                }
            }
        } catch (DataIntegrityViolationException exception) {
            // Already processed -- processor's own transaction (block/unblock
            // write included) already rolled back; ack and move on, not an
            // error. Also covers the race where two different events try to
            // block the same associationId for the first time concurrently
            // (BlockedAssociationRepositoryAdapter.block()'s check-then-act --
            // Task 35's user-flagged finding): the loser's transaction rolls
            // back cleanly here, and the winner has already left the
            // association correctly blocked.
        }
    }
}

package pe.esgtrazabilidad.collection.events.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.kernel.amqp.OutboxDispatcher;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

/**
 * Spec A.5a: the outbox of collection-service retries a publication that failed. shared-kernel's
 * dispatcher marks a failed row FAILED and never drops it; this service's adapter counts FAILED
 * rows as pending, so the next cycle takes it again. Run here against the real adapter and a real
 * Postgres, with a broker that is down once and then up.
 *
 * <p>Finding recorded for the report: the outbox has no attempts counter (only NEW, PROCESSED,
 * FAILED), so a row that always fails is retried at every cycle forever. That is the contract of the
 * shared dispatcher; nothing was changed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class OutboxRetryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private JdbcTemplate jdbc;

    private String statusOf(UUID id) {
        return jdbc.queryForObject("select status from outbox_event_collection where id = ?", String.class, id);
    }

    @Test
    void aPublicationThatFailsOnceIsPublishedInTheNextCycle() {
        RabbitTemplate broker = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .doNothing()
                .when(broker)
                .convertAndSend(anyString(), eq("collection.record.registered"), anyString(), any(MessagePostProcessor.class));
        OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, broker, new TopicExchange("esg.events"));
        UUID id = UUID.randomUUID();
        outbox.save(new OutboxEntry(id, "CollectionRegisteredEvent", "collection.record.registered", "{\"recordId\":\"x\"}",
                OutboxStatus.NEW, Instant.now()));

        dispatcher.dispatchPending();
        assertThat(statusOf(id)).as("the failure is recorded, not lost").isEqualTo("FAILED");

        dispatcher.dispatchPending();
        assertThat(statusOf(id)).as("the next cycle picks it up again and publishes it").isEqualTo("PROCESSED");
        verify(broker, times(2))
                .convertAndSend(anyString(), eq("collection.record.registered"), anyString(), any(MessagePostProcessor.class));

        dispatcher.dispatchPending();
        verify(broker, times(2))
                .convertAndSend(anyString(), eq("collection.record.registered"), anyString(), any(MessagePostProcessor.class));
    }
}

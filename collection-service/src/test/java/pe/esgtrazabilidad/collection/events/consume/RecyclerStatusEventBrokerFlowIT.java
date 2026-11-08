package pe.esgtrazabilidad.collection.events.consume;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queue, its binding and the listener over a real broker. The producer's wire shape is
 * imitated by publishing the JSON through the same exchange recycler-service's dispatcher
 * uses (collection-service never depends on recycler-service, not even in tests).
 *
 * "Nothing changed" is proven without sleeping: events of one queue are consumed in order by
 * one consumer, so once a later sentinel event for another recycler has been applied, every
 * earlier event has already been processed.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
        properties = {
            "esg.events.outbox-dispatcher.enabled=false",
            "spring.rabbitmq.listener.simple.auto-startup=true",
            "JWT_SECRET=" + TestJwtTokens.TEST_SECRET
        })
@DirtiesContext
class RecyclerStatusEventBrokerFlowIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TopicExchange eventsExchange;

    @Autowired
    private RecyclerStatusCopyRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private void publish(UUID recyclerId, RecyclerStatus status, Instant occurredAt) throws Exception {
        String json = objectMapper.writeValueAsString(
                new RecyclerStatusChangedEvent(UUID.randomUUID(), occurredAt, recyclerId, UUID.randomUUID(), status));
        rabbitTemplate.convertAndSend(eventsExchange.getName(), "recycler.status.changed", json, message -> {
            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return message;
        });
    }

    private static void await(BooleanSupplier condition, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (!condition.getAsBoolean()) {
            if (Instant.now().isAfter(deadline)) {
                throw new AssertionError("La condición no se cumplió en " + timeout);
            }
            Thread.sleep(100);
        }
    }

    @Test
    void aNewRecyclersEventOverTheRealBrokerCreatesItsCopyWithoutManualSteps() throws Exception {
        UUID recyclerId = UUID.randomUUID();

        publish(recyclerId, RecyclerStatus.ACTIVE, Instant.now());

        await(() -> repository.isActive(recyclerId), Duration.ofSeconds(30));
    }

    @Test
    void anOlderEventArrivingAfterANewerOneLeavesTheNewerState() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        Instant now = Instant.now();

        publish(recyclerId, RecyclerStatus.INACTIVE, now.plusSeconds(60));
        publish(recyclerId, RecyclerStatus.ACTIVE, now); // older, delivered later
        publish(sentinel, RecyclerStatus.ACTIVE, now);
        await(() -> repository.isActive(sentinel), Duration.ofSeconds(30));

        // Both earlier events were consumed before the sentinel; the older one must not win.
        assertThat(repository.isActive(recyclerId)).isFalse();
    }

    @Test
    void aRedeliveredEventDoesNotRevertALaterChange() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID sentinel = UUID.randomUUID();
        Instant t0 = Instant.now();

        publish(recyclerId, RecyclerStatus.ACTIVE, t0);
        await(() -> repository.isActive(recyclerId), Duration.ofSeconds(30));
        publish(recyclerId, RecyclerStatus.INACTIVE, t0.plusSeconds(5));
        await(() -> !repository.isActive(recyclerId), Duration.ofSeconds(30));

        publish(recyclerId, RecyclerStatus.ACTIVE, t0); // the first one again
        publish(sentinel, RecyclerStatus.ACTIVE, t0);
        await(() -> repository.isActive(sentinel), Duration.ofSeconds(30));

        assertThat(repository.isActive(recyclerId)).isFalse();
    }
}

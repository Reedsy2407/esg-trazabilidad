package pe.esgtrazabilidad.reporting.events.consume;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real-broker half of this service's own consumption proof, mirroring
 * recycler-service's own CollectionRegisteredEventBrokerFlowIT (Task 31).
 * collection-service never depends on reporting-service (even in tests), so
 * this can't boot a real collection-service Spring context -- instead this
 * publishes a structurally-matching JSON payload directly via RabbitTemplate,
 * exactly as collection-service's own OutboxDispatcher would, and lets this
 * service's REAL @RabbitListener (Task 48) consume it.
 *
 * spring.rabbitmq.listener.simple.auto-startup=true overrides the test-JVM
 * default (false, set in pom.xml for every OTHER IT that doesn't need a
 * live listener) specifically for this class, so the listener container
 * actually connects and consumes here.
 *
 * @DirtiesContext: without forcing a real ApplicationContext.close() right
 * after this class's tests finish, Spring's context caching keeps this
 * context (and its live listener container) alive until the whole test JVM
 * exits -- reconnect-looping against Testcontainers RabbitMQ this class's
 * own @Container lifecycle has already stopped.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@DirtiesContext
class CollectionRegisteredEventBrokerFlowIT {

    private static final String ROUTING_KEY = "collection.record.registered";

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
    private TracedCollectionEntryJpaRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String eventJson(UUID eventId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg)
            throws JsonProcessingException {
        CollectionRegisteredEvent event = new CollectionRegisteredEvent(
                eventId, Instant.now(), UUID.randomUUID(), UUID.randomUUID(), associationId, collectionDate, weightKg);
        return objectMapper.writeValueAsString(event);
    }

    private void publish(String json) {
        rabbitTemplate.convertAndSend(eventsExchange.getName(), ROUTING_KEY, json, message -> {
            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return message;
        });
    }

    private BigDecimal periodSum(UUID associationId, LocalDate periodStart, LocalDate periodEnd) {
        return repository.findByAssociationIdAndCollectionDateBetween(associationId, periodStart, periodEnd).stream()
                .map(entry -> entry.getWeightKg())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal awaitNonZeroSum(UUID associationId, LocalDate periodStart, LocalDate periodEnd, Duration timeout)
            throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        BigDecimal last;
        do {
            last = periodSum(associationId, periodStart, periodEnd);
            if (last.compareTo(BigDecimal.ZERO) > 0) {
                return last;
            }
            Thread.sleep(200);
        } while (Instant.now().isBefore(deadline));
        return last;
    }

    @Test
    void aMessagePublishedToTheRealBrokerLandsInTheLedger() throws Exception {
        UUID associationId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2026, 4, 15);

        publish(eventJson(UUID.randomUUID(), associationId, collectionDate, new BigDecimal("7.25")));

        // Generous timeout: same reasoning as every other broker-flow IT in
        // this codebase (Task 31) -- a tight timeout that passes in
        // isolation can flake under the full suite's Testcontainers
        // contention.
        BigDecimal sum = awaitNonZeroSum(
                associationId, collectionDate.withDayOfMonth(1), collectionDate.withDayOfMonth(30), Duration.ofSeconds(30));
        assertThat(sum).isEqualByComparingTo("7.25");
    }

    @Test
    void redeliveringTheSameMessageOverTheRealBrokerDoesNotDoubleApplyItToThePeriodSum() throws Exception {
        UUID associationId = UUID.randomUUID();
        LocalDate collectionDate = LocalDate.of(2026, 5, 10);
        LocalDate periodStart = collectionDate.withDayOfMonth(1);
        LocalDate periodEnd = collectionDate.withDayOfMonth(31);
        UUID eventId = UUID.randomUUID();
        String json = eventJson(eventId, associationId, collectionDate, new BigDecimal("9.00"));

        publish(json);
        awaitNonZeroSum(associationId, periodStart, periodEnd, Duration.ofSeconds(30));

        publish(json); // same eventId -- simulated broker redelivery
        // No positive signal to await for a no-op -- give the second
        // delivery a real window to be (correctly) rejected before asserting.
        Thread.sleep(5000);

        // Asserts the period-scoped SUM, not just that the row count didn't
        // grow -- the actual value CertificateService (Phase 22) will read.
        assertThat(periodSum(associationId, periodStart, periodEnd)).isEqualByComparingTo("9.00");
    }
}

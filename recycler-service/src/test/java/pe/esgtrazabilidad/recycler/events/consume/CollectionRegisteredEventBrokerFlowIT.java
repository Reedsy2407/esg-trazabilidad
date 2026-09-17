package pe.esgtrazabilidad.recycler.events.consume;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The other half of Direction A's proof, over a REAL broker (Testcontainers
 * RabbitMQ), not a direct Java call like CollectionRegisteredEventListenerIT.
 * collection-service never depends on recycler-service (even in tests), so
 * this can't boot a real collection-service Spring context -- instead this
 * publishes a structurally-matching JSON payload directly via RabbitTemplate,
 * exactly as collection-service's own OutboxDispatcher would, and lets this
 * service's REAL @RabbitListener consume it. Task 28 already proved with
 * real evidence that collection-service's actual wire payload has exactly
 * this shape (no extra fields), so this is a faithful stand-in for the real
 * producer.
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
 * own @Container lifecycle has already stopped, logging noise for the rest
 * of the module's test run (confirmed: it was still retrying during later,
 * unrelated test classes before this was added).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(
        properties = {
            "esg.events.outbox-dispatcher.enabled=false",
            "spring.rabbitmq.listener.simple.auto-startup=true"
        })
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
    private AssociationRepository associationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private BigDecimal totalKilosCollectedInDb(UUID associationId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_kilos_collected FROM association WHERE id = ?", BigDecimal.class, associationId);
    }

    private String eventJson(UUID eventId, UUID associationId, BigDecimal weightKg) throws JsonProcessingException {
        CollectionRegisteredEvent event = new CollectionRegisteredEvent(
                eventId, Instant.now(), UUID.randomUUID(), UUID.randomUUID(), associationId, LocalDate.now(), weightKg);
        return objectMapper.writeValueAsString(event);
    }

    private void publish(String json) {
        rabbitTemplate.convertAndSend(eventsExchange.getName(), ROUTING_KEY, json, message -> {
            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return message;
        });
    }

    private BigDecimal awaitNonZeroTotal(UUID associationId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        BigDecimal last;
        do {
            last = totalKilosCollectedInDb(associationId);
            if (last.compareTo(BigDecimal.ZERO) > 0) {
                return last;
            }
            Thread.sleep(200);
        } while (Instant.now().isBefore(deadline));
        return last;
    }

    @Test
    void aMessagePublishedToTheRealBrokerIncrementsTotalKilos() throws Exception {
        Association association = associationRepository.save(Association.create(
                "Asociación broker IT", "20133333333", "REG-BRK-1", "Dirección", "a@b.pe", "999999999"));

        publish(eventJson(UUID.randomUUID(), association.getId(), new BigDecimal("7.25")));

        // Generous timeout: confirmed via collection-service's own equivalent
        // test that a tight timeout here can flake under the full suite's
        // heavier Testcontainers load, even though it passes reliably in
        // isolation.
        BigDecimal total = awaitNonZeroTotal(association.getId(), Duration.ofSeconds(30));
        assertThat(total).isEqualByComparingTo("7.25");
    }

    @Test
    void redeliveringTheSameMessageOverTheRealBrokerDoesNotDoubleApplyIt() throws Exception {
        Association association = associationRepository.save(Association.create(
                "Asociación broker IT 2", "20122222222", "REG-BRK-2", "Dirección", "a@b.pe", "999999999"));
        UUID eventId = UUID.randomUUID();
        String json = eventJson(eventId, association.getId(), new BigDecimal("9.00"));

        publish(json);
        awaitNonZeroTotal(association.getId(), Duration.ofSeconds(30));

        publish(json); // same eventId -- simulated broker redelivery
        // No positive signal to await for a no-op -- give the second
        // delivery a real window to be (correctly) rejected before asserting.
        Thread.sleep(5000);

        assertThat(totalKilosCollectedInDb(association.getId())).isEqualByComparingTo("9.00");
    }
}

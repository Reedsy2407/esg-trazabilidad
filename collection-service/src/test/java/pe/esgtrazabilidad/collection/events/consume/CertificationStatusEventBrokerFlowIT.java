package pe.esgtrazabilidad.collection.events.consume;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * Direction B's end-to-end proof, over a REAL broker (Testcontainers
 * RabbitMQ) -- collection-service never depends on recycler-service (even
 * in tests), so this can't boot a real recycler-service context. Instead it
 * publishes structurally-matching JSON payloads directly via RabbitTemplate,
 * exactly as recycler-service's OutboxDispatcher would (its own companion
 * test, recycler-service's CertificationExpiryPublishFlowIT, already proves
 * that's a faithful stand-in for the real producer's wire shape), and lets
 * this service's REAL CertificationStatusEventListener (Task 36) consume
 * it -- then exercises the REAL HTTP enforcement path
 * (CollectionRecordService.create(), Task 37) against whatever state that
 * consumption left behind. Same package as the listener and its local event
 * records (Direction A's CollectionRegisteredEventBrokerFlowIT precedent),
 * so those package-private records can be reused directly.
 *
 * spring.rabbitmq.listener.simple.auto-startup=true and @DirtiesContext:
 * same reasoning as CollectionRegisteredEventBrokerFlowIT (Task 31) --
 * this class's context is the only one running a live listener against
 * Testcontainers RabbitMQ, and it must be closed right after this class's
 * tests finish or Spring's context caching keeps it reconnect-looping for
 * the rest of the module's test run.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "esg.events.outbox-dispatcher.enabled=false",
            "spring.rabbitmq.listener.simple.auto-startup=true"
        })
@DirtiesContext
class CertificationStatusEventBrokerFlowIT {

    private static final String EXPIRED_ROUTING_KEY = "certification.expired";
    private static final String RENEWED_ROUTING_KEY = "certification.renewed";

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

    @LocalServerPort
    private int port;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TopicExchange eventsExchange;

    @Autowired
    private BlockedAssociationRepository blockedAssociationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private String createNeighborRequest(String fullName) {
        return """
                {
                  "fullName": "%s",
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "district": "Surco"
                }
                """.formatted(fullName);
    }

    private String createNeighbor(String fullName) {
        RestAssured.port = port;
        return given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest(fullName))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createRecordRequest(String associationId) {
        return """
                {
                  "associationId": "%s",
                  "collectionDate": "2026-01-05",
                  "weightKg": 5
                }
                """.formatted(associationId);
    }

    private void publish(String routingKey, String json) {
        rabbitTemplate.convertAndSend(eventsExchange.getName(), routingKey, json, message -> {
            message.getMessageProperties().setContentType(MessageProperties.CONTENT_TYPE_JSON);
            return message;
        });
    }

    private String expiredEventJson(UUID eventId, UUID certificationId, UUID associationId)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(new CertificationExpiredEvent(
                eventId, Instant.now(), certificationId, associationId, LocalDate.now().minusDays(1)));
    }

    private String renewedEventJson(UUID eventId, UUID certificationId, UUID associationId)
            throws JsonProcessingException {
        return objectMapper.writeValueAsString(new CertificationRenewedEvent(
                eventId, Instant.now(), certificationId, associationId, LocalDate.now().plusYears(1)));
    }

    // Generous timeout, same reasoning as every other broker-flow IT in this
    // codebase (Task 31): a tight timeout that passes in isolation can flake
    // under the full suite's Testcontainers contention.
    private boolean awaitBlocked(UUID associationId, boolean expected, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        boolean last;
        do {
            last = blockedAssociationRepository.isBlocked(associationId);
            if (last == expected) {
                return last;
            }
            Thread.sleep(200);
        } while (Instant.now().isBefore(deadline));
        return last;
    }

    @Test
    void anExpiredEventBlocksTheAssociationAndEnforces409ThenARenewedEventUnblocksAndAllowsCreation()
            throws Exception {
        String neighborId = createNeighbor("Ana Torres Direction B IT");
        UUID associationId = UUID.randomUUID();
        UUID certificationId = UUID.randomUUID();

        publish(EXPIRED_ROUTING_KEY, expiredEventJson(UUID.randomUUID(), certificationId, associationId));
        assertThat(awaitBlocked(associationId, true, Duration.ofSeconds(30))).isTrue();

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(associationId.toString()))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-009"));

        publish(RENEWED_ROUTING_KEY, renewedEventJson(UUID.randomUUID(), certificationId, associationId));
        assertThat(awaitBlocked(associationId, false, Duration.ofSeconds(30))).isFalse();

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(associationId.toString()))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201);
    }

    @Test
    void redeliveringTheSameExpiredEventOverTheRealBrokerLeavesTheBlockStateUnchanged() throws Exception {
        UUID associationId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String json = expiredEventJson(eventId, UUID.randomUUID(), associationId);

        publish(EXPIRED_ROUTING_KEY, json);
        assertThat(awaitBlocked(associationId, true, Duration.ofSeconds(30))).isTrue();

        publish(EXPIRED_ROUTING_KEY, json); // same eventId -- simulated broker redelivery
        // No positive signal to await for a no-op -- give the second
        // delivery a real window to be (correctly) rejected before asserting.
        Thread.sleep(5000);

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
    }

    @Test
    void aFullExpireRenewReExpireCycleReBlocksOnADistinctSecondExpiredEvent() throws Exception {
        UUID associationId = UUID.randomUUID();
        UUID certificationId = UUID.randomUUID();

        publish(EXPIRED_ROUTING_KEY, expiredEventJson(UUID.randomUUID(), certificationId, associationId));
        assertThat(awaitBlocked(associationId, true, Duration.ofSeconds(30))).isTrue();

        publish(RENEWED_ROUTING_KEY, renewedEventJson(UUID.randomUUID(), certificationId, associationId));
        assertThat(awaitBlocked(associationId, false, Duration.ofSeconds(30))).isFalse();

        // A DISTINCT eventId -- the real-world equivalent of the certification
        // expiring again after renewal (CertificationExpiryScanJob's own
        // notifiedExpiredAt reset on renew(), proven in Task 32/33, makes this
        // a genuinely new event on the producer side). Proves block() correctly
        // re-inserts after unblock() deleted the row, not just updates a
        // lingering one left behind by the first block.
        publish(EXPIRED_ROUTING_KEY, expiredEventJson(UUID.randomUUID(), certificationId, associationId));
        assertThat(awaitBlocked(associationId, true, Duration.ofSeconds(30))).isTrue();
    }
}

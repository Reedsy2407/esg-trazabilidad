package pe.esgtrazabilidad.collection.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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

import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The publish half of Direction A's real-broker proof (the consume half is
 * recycler-service's own CollectionRegisteredEventBrokerFlowIT). Never
 * boots a real recycler-service context -- collection-service doesn't
 * depend on it, even in tests -- so this instead declares its own
 * throwaway queue bound to the same exchange/routing key recycler-service's
 * real listener uses, and inspects what actually arrives on the wire.
 *
 * esg.events.outbox-dispatcher.enabled=true overrides the test-JVM default
 * (false, set in pom.xml for every OTHER IT that doesn't need the live
 * dispatcher) specifically for this class; interval-ms is shortened so the
 * test doesn't wait a full 5s tick.
 *
 * @DirtiesContext: this class's context is the only one running the
 * dispatcher on a fast 500ms tick against Testcontainers Postgres/RabbitMQ.
 * Without forcing a real ApplicationContext.close() right after this class's
 * tests finish, Spring's context caching keeps it (and the dispatcher's
 * scheduled task) alive until the whole test JVM exits -- ticking against
 * containers Testcontainers has already stopped, and logging connection-
 * refused noise for up to 30s per tick until Surefire force-kills the JVM.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "esg.events.outbox-dispatcher.enabled=true",
            "esg.events.outbox-dispatcher.interval-ms=500"
        })
@DirtiesContext
class CollectionRegisteredEventPublishFlowIT {

    private static final String EXCHANGE = "esg-trazabilidad.events";
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

    @LocalServerPort
    private int port;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

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

    private String createRecordRequest(String associationId, String date, String weight) {
        return """
                {
                  "associationId": "%s",
                  "collectionDate": "%s",
                  "weightKg": %s
                }
                """.formatted(associationId, date, weight);
    }

    // Non-exclusive, so it can be declared on one pooled channel and
    // consumed from another via a later, separate RabbitTemplate call.
    private String declareThrowawayQueueBoundToTheRoutingKey() {
        return rabbitTemplate.execute((Channel channel) -> {
            String queueName = channel.queueDeclare("", false, false, true, null).getQueue();
            channel.queueBind(queueName, EXCHANGE, ROUTING_KEY);
            return queueName;
        });
    }

    @Test
    void creatingACollectionRecordPublishesAMessageToTheRealBroker() throws Exception {
        String testQueue = declareThrowawayQueueBoundToTheRoutingKey();
        String neighborId = createNeighbor("Ana Torres Broker IT");
        String associationId = UUID.randomUUID().toString();

        String recordId = given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(associationId, "2026-01-05", "6.75"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        // Generous timeout: this test runs alongside several other
        // Testcontainers-heavy IT classes in the full suite, and on a
        // resource-contended machine the dispatcher's first tick (every
        // 500ms once it's running) can be delayed well past what looks
        // sufficient in isolation -- confirmed by a real failure here
        // (message never arrived within 10s) that did not reproduce when
        // this test ran alone.
        Message message = rabbitTemplate.receive(testQueue, 30_000);
        assertThat(message).isNotNull();
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        Map<String, Object> payload = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
        assertThat(payload.get("recordId")).isEqualTo(recordId);
        assertThat(payload.get("associationId")).isEqualTo(associationId);
        assertThat(payload.get("weightKg")).isEqualTo(6.75);
    }
}

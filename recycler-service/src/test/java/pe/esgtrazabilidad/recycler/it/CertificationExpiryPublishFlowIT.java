package pe.esgtrazabilidad.recycler.it;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
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

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * The publish half of Direction B's real-broker proof (the consume half is
 * collection-service's own CertificationStatusEventBrokerFlowIT). Never
 * boots a real collection-service context -- recycler-service doesn't
 * depend on it, even in tests -- so this instead declares its own
 * throwaway queue bound to a wildcard pattern covering both routing keys
 * (certification.expired, certification.renewed) recycler-service's real
 * publishers use, and inspects what actually arrives on the wire.
 *
 * Unlike Direction A's equivalent (CollectionRegisteredEventPublishFlowIT),
 * there are TWO real scheduled hops to prove here, not one:
 * CertificationExpiryScanJob's own tick (Task 33) has to find the expired
 * certification and write the outbox row before OutboxDispatcher's tick
 * (Task 26) can send it -- both intervals are shortened so the test doesn't
 * wait a full hour + 5s.
 *
 * @DirtiesContext: same reasoning as every other broker-flow IT in this
 * codebase -- this class's context is the only one running two live
 * @Scheduled jobs on fast ticks against Testcontainers Postgres/RabbitMQ;
 * without forcing a real close, Spring's context caching keeps them alive
 * (and reconnect-looping) for the rest of the module's test run.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "esg.events.outbox-dispatcher.enabled=true",
            "esg.events.outbox-dispatcher.interval-ms=500",
            "esg.certification.expiry-scan.interval-ms=500"
        })
@DirtiesContext
class CertificationExpiryPublishFlowIT {

    private static final String EXCHANGE = "esg-trazabilidad.events";
    private static final String CERTIFICATION_ROUTING_PATTERN = "certification.*";

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

    private String createAssociationRequest(String ruc) {
        return """
                {
                  "name": "Asociación Direction B IT",
                  "ruc": "%s",
                  "registrationNumber": "REG-DIRB-%s",
                  "address": "Dirección de prueba",
                  "contactEmail": "it@test.pe",
                  "contactPhone": "999999999"
                }
                """.formatted(ruc, ruc);
    }

    private String createAssociation(String ruc) {
        return given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest(ruc))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createCertificationRequest(String issuedDate, String expirationDate) {
        return """
                {
                  "certificationType": "ISO 14001",
                  "issuedDate": "%s",
                  "expirationDate": "%s"
                }
                """.formatted(issuedDate, expirationDate);
    }

    // Non-exclusive, so it can be declared on one pooled channel and
    // consumed from another via a later, separate RabbitTemplate call.
    //
    // Real gap this fixes: autoDelete=true (Direction A's original
    // declareThrowawayQueueBoundToTheRoutingKey helper) deletes the queue
    // the moment its consumer count drops back to zero -- fine for a test
    // that calls rabbitTemplate.receive() exactly once, but this test calls
    // it TWICE on the same queue (the expired event, then the renewed
    // event), and each receive() attaches then detaches a temporary
    // consumer. The queue was gone by the second call
    // ("NOT_FOUND - no queue ... in vhost '/'"), confirmed via a real
    // failing run before this was set to autoDelete=false. Harmless to
    // leave non-auto-deleting -- the whole Testcontainers RabbitMQ broker
    // is discarded when this test class's container stops.
    private String declareThrowawayQueueBoundToCertificationEvents() {
        return rabbitTemplate.execute((Channel channel) -> {
            String queueName = channel.queueDeclare("", false, false, false, null).getQueue();
            channel.queueBind(queueName, EXCHANGE, CERTIFICATION_ROUTING_PATTERN);
            return queueName;
        });
    }

    @Test
    void anExpiredCertificationIsScannedAndPublishedThenItsRenewalPublishesASecondEvent() throws Exception {
        String testQueue = declareThrowawayQueueBoundToCertificationEvents();
        String associationId = createAssociation("20909090901");
        String issuedDate = LocalDate.now().minusYears(2).toString();
        String expiredDate = LocalDate.now().minusDays(1).toString();

        String certificationId = given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expiredDate))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(201)
                .body("expired", equalTo(true))
                .extract()
                .path("id");

        // Generous timeout: two real scheduled hops (scan job, then
        // dispatcher) plus this test runs alongside several other
        // Testcontainers-heavy IT classes in the full suite -- a tight
        // timeout that passes in isolation has already flaked under the
        // full suite's contention for Direction A's equivalent test (Task 31).
        Message expiredMessage = rabbitTemplate.receive(testQueue, 30_000);
        assertThat(expiredMessage).isNotNull();
        Map<String, Object> expiredPayload = objectMapper.readValue(expiredMessage.getBody(), new TypeReference<>() {});
        assertThat(expiredPayload.get("certificationId")).isEqualTo(certificationId);
        assertThat(expiredPayload.get("associationId")).isEqualTo(associationId);

        String newExpirationDate = LocalDate.now().plusYears(1).toString();
        String renewRequest = """
                {
                  "newExpirationDate": "%s"
                }
                """.formatted(newExpirationDate);

        given()
                .contentType(ContentType.JSON)
                .body(renewRequest)
                .when()
                .patch("/associations/{associationId}/certifications/{id}/renew", associationId, certificationId)
                .then()
                .statusCode(200);

        // The renewed event is published inline within renew()'s own
        // transaction (Task 34), not on a scheduled tick -- only
        // OutboxDispatcher's own tick still needs to fire.
        Message renewedMessage = rabbitTemplate.receive(testQueue, 30_000);
        assertThat(renewedMessage).isNotNull();
        Map<String, Object> renewedPayload = objectMapper.readValue(renewedMessage.getBody(), new TypeReference<>() {});
        assertThat(renewedPayload.get("certificationId")).isEqualTo(certificationId);
        assertThat(renewedPayload.get("associationId")).isEqualTo(associationId);
        assertThat(renewedPayload.get("newExpirationDate")).isEqualTo(newExpirationDate);
    }
}

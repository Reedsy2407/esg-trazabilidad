package pe.esgtrazabilidad.collection.events.consume;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.restassured.http.ContentType;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;
import pe.esgtrazabilidad.collection.events.ledger.CertificationStatusLedgerJpaRepository;

import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

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
            "spring.rabbitmq.listener.simple.auto-startup=true",
            "JWT_SECRET=" + TestJwtTokens.TEST_SECRET
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

    @Autowired
    private CertificationStatusLedgerJpaRepository certificationStatusLedgerJpaRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    // isBlocked() alone can't distinguish "dedup worked" from "dedup is
    // completely broken": BlockedAssociationRepositoryAdapter.block()
    // unconditionally upserts on every call, so a second (supposedly
    // deduped) delivery would still read isBlocked()==true even if the
    // ledger's own event_id dedup never ran at all. blocked_at DOES change
    // on every successful block() call, so reading it directly (same
    // UTC-Calendar pattern as BlockedAssociationRepositoryAdapterIT, to
    // avoid pgjdbc reinterpreting the stored UTC timestamp in the JVM's
    // local zone) is real evidence a second delivery's transaction never
    // reached its own block() write.
    private Instant blockedAtOf(UUID associationId) {
        return jdbcTemplate.queryForObject(
                "SELECT blocked_at FROM blocked_association WHERE association_id = ?",
                (rs, rowNum) -> rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant(),
                associationId);
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
        RestAssuredSetup.authenticated(port);
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

    private boolean awaitLedgered(UUID eventId, Duration timeout) throws InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        do {
            if (certificationStatusLedgerJpaRepository.existsById(eventId)) {
                return true;
            }
            Thread.sleep(200);
        } while (Instant.now().isBefore(deadline));
        return false;
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
        Instant blockedAtAfterFirstDelivery = blockedAtOf(associationId);

        publish(EXPIRED_ROUTING_KEY, json); // same eventId -- simulated broker redelivery
        // No positive signal to await for a no-op -- give the second
        // delivery a real window to be (correctly) rejected before asserting.
        Thread.sleep(5000);

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
        assertThat(blockedAtOf(associationId)).isEqualTo(blockedAtAfterFirstDelivery);
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

    @Test
    void publishingExpiredThenRenewedInQuickSuccessionThroughTheOutboxLeavesTheFinalStateUnblocked()
            throws Exception {
        UUID associationId = UUID.randomUUID();
        UUID certificationId = UUID.randomUUID();
        UUID expiredEventId = UUID.randomUUID();
        UUID renewedEventId = UUID.randomUUID();

        // Task 39's ordering test (SPEC-cross-service-events.md, "Ordering
        // test (best-effort, documented limit)"). Unlike the happy-path test
        // above, which deliberately awaits the intermediate blocked state
        // before publishing the renewed event, this publishes BOTH back to
        // back with no wait in between -- the "quick succession" the spec
        // asks for. publish() here (rabbitTemplate.convertAndSend with the
        // JSON content type set) is the exact same faithful stand-in for the
        // real OutboxDispatcher's own wire behaviour already established by
        // this class's other tests (see the class-level Javadoc): a single
        // dispatcher publishes each service's outbox strictly in insertion
        // order, one row at a time, waiting for the publish to succeed
        // before advancing (SPEC's own Resolved Decisions) -- so two rows
        // written moments apart still reach the wire, and this listener's
        // single-consumer queue (default @RabbitListener concurrency, no
        // concurrency override anywhere in this class), in that same order.
        // If the dispatcher ever processed its outbox out of order, this
        // test would fail here: a reversed delivery (renewed before expired)
        // leaves the association BLOCKED, not unblocked, since the renewed
        // event's unblock() would be a no-op (nothing blocked yet) and the
        // expired event's block() would run last.
        //
        // Explicitly NOT covered (documented, accepted residual risk,
        // matching the spec's own wording): true out-of-order delivery after
        // a broker-level redelivery. If RabbitMQ redelivers the renewed
        // event before the expired event's own first-time delivery is ever
        // acknowledged -- e.g. a consumer crash between the two -- nothing
        // in this design prevents the renewed event from being processed
        // first, incorrectly leaving a since-renewed association blocked.
        // Closing that gap would need per-aggregate sequence numbers or a
        // saga, disproportionate to this MVP's scope (SPEC-cross-service-
        // events.md, Resolved Decisions: "Ordering risk is mitigated, not
        // eliminated").
        publish(EXPIRED_ROUTING_KEY, expiredEventJson(expiredEventId, certificationId, associationId));
        publish(RENEWED_ROUTING_KEY, renewedEventJson(renewedEventId, certificationId, associationId));

        // Both events reaching the ledger proves the listener actually
        // processed them -- final state alone (checked below) would be
        // false/unblocked vacuously if nothing had been consumed at all,
        // since unblocked is also this association's untouched starting
        // state.
        assertThat(awaitLedgered(expiredEventId, Duration.ofSeconds(30))).isTrue();
        assertThat(awaitLedgered(renewedEventId, Duration.ofSeconds(30))).isTrue();
        assertThat(awaitBlocked(associationId, false, Duration.ofSeconds(30))).isFalse();
    }
}

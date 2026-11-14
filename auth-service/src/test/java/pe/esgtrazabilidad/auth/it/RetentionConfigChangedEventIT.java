package pe.esgtrazabilidad.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.kernel.amqp.OutboxDispatcher;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;

/** CA-41, issuer side: every save of the periods by the ADMIN announces them, in the same transaction. */
class RetentionConfigChangedEventIT extends RecyclerApiIT {

    private static final String ROUTING_KEY = "auth.retention.changed";

    @Autowired
    private OutboxRepository outbox;

    @Autowired
    private ObjectMapper objectMapper;

    /** Other ITs of the JVM save the periods too (and leave their events): start from a clean outbox. */
    @BeforeEach
    void emptyTheOutbox() {
        jdbc.update("delete from outbox_event_auth where routing_key = ?", ROUTING_KEY);
    }

    @AfterEach
    void restoreDefaults() {
        jdbc.update("update retention_config set gps_days = 90, photo_months = 12, consent_version = 1 where id = 1");
        jdbc.update("delete from outbox_event_auth where routing_key = ?", ROUTING_KEY);
    }

    private List<String> payloads() {
        return jdbc.queryForList(
                "select payload_json from outbox_event_auth where routing_key = ? order by created_at, id", String.class, ROUTING_KEY);
    }

    @Test
    void savingTheTermsWritesOneEventWithTheStoredValuesAndTheConsentVersion() throws Exception {
        as(staffToken(StaffRole.ADMIN)).body(Map.of("gpsDays", 60, "photoMonths", 6)).put("/auth/retention-config")
                .then().statusCode(200);

        assertThat(payloads()).hasSize(1);
        JsonNode event = objectMapper.readTree(payloads().get(0));
        assertThat(event.fieldNames()).toIterable()
                .containsExactlyInAnyOrder("eventId", "occurredAt", "gpsDays", "photoMonths", "consentVersion");
        assertThat(event.get("gpsDays").asInt()).isEqualTo(60);
        assertThat(event.get("photoMonths").asInt()).isEqualTo(6);
        assertThat(event.get("consentVersion").asInt()).isEqualTo(2);
        assertThat(event.get("eventId").asText()).isNotBlank();
        assertThat(event.get("occurredAt").asText()).as("an ISO instant, not a number").contains("T");
    }

    @Test
    void savingTheSameValuesAgainStillAnnouncesThemWithoutRaisingTheConsentVersion() throws Exception {
        String admin = staffToken(StaffRole.ADMIN);
        // The human's first save after the deployment: the same values the system already has.
        as(admin).body(Map.of("gpsDays", 90, "photoMonths", 12)).put("/auth/retention-config").then().statusCode(200);
        as(admin).body(Map.of("gpsDays", 90, "photoMonths", 12)).put("/auth/retention-config").then().statusCode(200);

        assertThat(payloads()).hasSize(2);
        for (String payload : payloads()) {
            JsonNode event = objectMapper.readTree(payload);
            assertThat(event.get("gpsDays").asInt()).isEqualTo(90);
            assertThat(event.get("consentVersion").asInt()).isEqualTo(1);
        }
        assertThat(objectMapper.readTree(payloads().get(0)).get("eventId"))
                .isNotEqualTo(objectMapper.readTree(payloads().get(1)).get("eventId"));
    }

    @Test
    void aRejectedSaveAnnouncesNothing() {
        String admin = staffToken(StaffRole.ADMIN);
        as(admin).body(Map.of("gpsDays", 5, "photoMonths", 12)).put("/auth/retention-config").then().statusCode(400);
        as(staffToken(StaffRole.STAFF)).body(Map.of("gpsDays", 60, "photoMonths", 6)).put("/auth/retention-config")
                .then().statusCode(403);

        assertThat(payloads()).isEmpty();
    }

    @Test
    void ifTheEventCannotBeWrittenTheNewTermsAreNotSavedEither() {
        jdbc.execute("""
                create function it_fail_outbox_auth() returns trigger language plpgsql as
                $$ begin raise exception 'forced failure of the outbox insert'; end $$
                """);
        jdbc.execute("create trigger it_fail_outbox_auth before insert on outbox_event_auth for each row execute function it_fail_outbox_auth()");
        try {
            as(staffToken(StaffRole.ADMIN)).body(Map.of("gpsDays", 77, "photoMonths", 7)).put("/auth/retention-config")
                    .then().statusCode(org.hamcrest.Matchers.greaterThanOrEqualTo(500));
        } finally {
            jdbc.execute("drop trigger it_fail_outbox_auth on outbox_event_auth");
            jdbc.execute("drop function it_fail_outbox_auth()");
        }

        assertThat(jdbc.queryForMap("select gps_days, photo_months, consent_version from retention_config where id = 1"))
                .as("single transaction: no new terms without their announcement")
                .containsEntry("gps_days", 90).containsEntry("photo_months", 12).containsEntry("consent_version", 1);
        assertThat(payloads()).isEmpty();
    }

    @Test
    void theDispatcherPublishesTheEventAndRetriesItIfTheBrokerWasDown() {
        RabbitTemplate broker = mock(RabbitTemplate.class);
        doThrow(new AmqpConnectException(new RuntimeException("broker down")))
                .doNothing()
                .when(broker)
                .convertAndSend(anyString(), eq(ROUTING_KEY), anyString(), any(MessagePostProcessor.class));
        OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, broker, new TopicExchange("esg.events"));
        as(staffToken(StaffRole.ADMIN)).body(Map.of("gpsDays", 45, "photoMonths", 9)).put("/auth/retention-config")
                .then().statusCode(200);

        dispatcher.dispatchPending();
        assertThat(statusOfRetentionEvent()).as("a failed publication is recorded, not lost").isEqualTo("FAILED");
        dispatcher.dispatchPending();
        assertThat(statusOfRetentionEvent()).isEqualTo("PROCESSED");
        verify(broker, times(2)).convertAndSend(anyString(), eq(ROUTING_KEY), anyString(), any(MessagePostProcessor.class));
    }

    private String statusOfRetentionEvent() {
        return jdbc.queryForObject("select status from outbox_event_auth where routing_key = ?", String.class, ROUTING_KEY);
    }
}

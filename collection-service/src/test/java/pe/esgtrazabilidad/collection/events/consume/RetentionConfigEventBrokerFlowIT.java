package pe.esgtrazabilidad.collection.events.consume;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.BooleanSupplier;

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

import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy;
import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy.RetentionTerms;

/**
 * The queue, its binding and the listener over a real broker. auth-service's wire shape is imitated with
 * the literal JSON its event serialises to (collection-service never depends on auth-service, not even in
 * tests). "Nothing changed" is proven without sleeping: one consumer takes one queue in order, so once a
 * later sentinel announcement has been applied, every earlier one has been processed.
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
class RetentionConfigEventBrokerFlowIT {

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
    private RetentionSettingsCopy settings;

    @Autowired
    private JdbcTemplate jdbc;

    private void publish(int gpsDays, int photoMonths, Instant occurredAt) {
        String json = "{\"eventId\":\"%s\",\"occurredAt\":\"%s\",\"gpsDays\":%d,\"photoMonths\":%d,\"consentVersion\":3}"
                .formatted(UUID.randomUUID(), occurredAt, gpsDays, photoMonths);
        rabbitTemplate.convertAndSend(eventsExchange.getName(), "auth.retention.changed", json, message -> {
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
    void anAnnouncementOverTheRealBrokerChangesTheTermsAndAnOlderOneDoesNot() throws Exception {
        jdbc.update("delete from retention_settings_copy");
        Instant now = Instant.now();

        publish(60, 6, now.plusSeconds(60));
        await(() -> settings.current().equals(new RetentionTerms(60, 6)), Duration.ofSeconds(30));

        publish(200, 30, now); // older, delivered later
        publish(45, 9, now.plusSeconds(120)); // sentinel: newer
        await(() -> settings.current().equals(new RetentionTerms(45, 9)), Duration.ofSeconds(30));
        assertThat(jdbc.queryForObject("select consent_version from retention_settings_copy where id = 1", Integer.class)).isEqualTo(3);
    }
}

package pe.esgtrazabilidad.auth.events.consume;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CA-11 (consumption) and CA-23 on the auth side, against a real Postgres: a new recycler's copy
 * appears with no manual step; an older or repeated event changes nothing; the final state does
 * not depend on arrival order, even with concurrent deliveries; no copy means not active.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RecyclerStatusEventListenerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RecyclerStatusEventListener listener;

    @Autowired
    private RecyclerStatusCopyRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Message messageFor(RecyclerStatusChangedEvent event) throws Exception {
        return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event)).build();
    }

    private static RecyclerStatusChangedEvent event(
            UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt) {
        return new RecyclerStatusChangedEvent(UUID.randomUUID(), occurredAt, recyclerId, associationId, status);
    }

    private boolean isActive(UUID recyclerId) {
        return repository.find(recyclerId).map(copy -> copy.isActive()).orElse(false);
    }

    private String statusOf(UUID recyclerId) {
        return jdbc.queryForObject("select status from recycler_status_copy_auth where recycler_id = ?", String.class, recyclerId);
    }

    private Instant lastOccurredAtOf(UUID recyclerId) {
        return jdbc.queryForObject(
                "select last_occurred_at from recycler_status_copy_auth where recycler_id = ?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                recyclerId);
    }

    private int rowsFor(UUID recyclerId) {
        return jdbc.queryForObject("select count(*) from recycler_status_copy_auth where recycler_id = ?", Integer.class, recyclerId);
    }

    @Test
    void aNewRecyclersCopyAppearsFromItsFirstEventAndKeepsTheAssociationFromTheEvent() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();

        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.ACTIVE, Instant.now())));

        var copy = repository.find(recyclerId).orElseThrow();
        assertThat(copy.isActive()).isTrue();
        assertThat(copy.associationId()).isEqualTo(associationId);
    }

    @Test
    void aRecyclerWithNoCopyHasNoRowAndIsNotActive() {
        UUID unknown = UUID.randomUUID();

        assertThat(repository.find(unknown)).isEmpty();
        assertThat(isActive(unknown)).isFalse();
    }

    @Test
    void aNewerEventUpdatesTheCopyBothWays() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        Instant t0 = Instant.now();

        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.ACTIVE, t0)));
        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.INACTIVE, t0.plusSeconds(1))));
        assertThat(isActive(recyclerId)).isFalse();

        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.ACTIVE, t0.plusSeconds(2))));
        assertThat(isActive(recyclerId)).isTrue();
    }

    @Test
    void anOlderEventThanTheStoredOneChangesNothing() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        Instant t0 = Instant.now();

        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.INACTIVE, t0.plusSeconds(10))));
        Instant stored = lastOccurredAtOf(recyclerId);

        listener.handle(messageFor(event(recyclerId, associationId, RecyclerStatus.ACTIVE, t0)));

        assertThat(statusOf(recyclerId)).isEqualTo("INACTIVE");
        assertThat(lastOccurredAtOf(recyclerId)).isEqualTo(stored);
    }

    @Test
    void aRepeatedEventChangesNothingEvenWithNanosecondPrecision() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        // Nanosecond digits: the table keeps microseconds, so a naive comparison would see the
        // redelivery as "newer" than what was stored.
        Instant withNanos = Instant.now().truncatedTo(ChronoUnit.SECONDS).plusNanos(123_456_789);
        RecyclerStatusChangedEvent first = event(recyclerId, UUID.randomUUID(), RecyclerStatus.ACTIVE, withNanos);

        listener.handle(messageFor(first));
        assertThat(repository.applyIfNewer(first.recyclerId(), first.associationId(), first.status(), first.occurredAt()))
                .as("the same event applied again must report no change")
                .isFalse();

        listener.handle(messageFor(first));
        assertThat(rowsFor(recyclerId)).isEqualTo(1);
        assertThat(lastOccurredAtOf(recyclerId)).isEqualTo(withNanos.truncatedTo(ChronoUnit.MICROS));
    }

    @Test
    void theFinalStateDoesNotDependOnArrivalOrderEvenWithConcurrentDeliveries() throws Exception {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        Instant base = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        List<RecyclerStatusChangedEvent> events = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            events.add(event(
                    recyclerId,
                    associationId,
                    i % 2 == 0 ? RecyclerStatus.ACTIVE : RecyclerStatus.INACTIVE,
                    base.plusMillis(i)));
        }
        // The newest event (index 39) is INACTIVE; whatever the arrival order, that must win.
        Collections.shuffle(events);

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (RecyclerStatusChangedEvent e : events) {
                futures.add(pool.submit(() -> {
                    start.await();
                    listener.handle(messageFor(e));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(statusOf(recyclerId)).isEqualTo("INACTIVE");
        assertThat(lastOccurredAtOf(recyclerId)).isEqualTo(base.plusMillis(39));
        assertThat(rowsFor(recyclerId)).isEqualTo(1);
    }

    @Test
    void anUnreadableOrIncompleteMessageIsDroppedWithoutFailingOrWritingAnything() {
        int before = jdbc.queryForObject("select count(*) from recycler_status_copy_auth", Integer.class);

        listener.handle(MessageBuilder.withBody("no es json".getBytes(StandardCharsets.UTF_8)).build());
        listener.handle(MessageBuilder.withBody("{\"status\":\"BORRADO\"}".getBytes(StandardCharsets.UTF_8)).build());
        listener.handle(MessageBuilder.withBody("{}".getBytes(StandardCharsets.UTF_8)).build());

        assertThat(jdbc.queryForObject("select count(*) from recycler_status_copy_auth", Integer.class)).isEqualTo(before);
    }
}

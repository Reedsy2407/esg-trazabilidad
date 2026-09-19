package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
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
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres, not mocked: proves correctness risks mocks can't reach --
 * same reasoning as CollectionRegisteredEventListenerIT (Task 31).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class CertificationStatusEventListenerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CertificationStatusEventListener listener;

    @Autowired
    private BlockedAssociationRepository blockedAssociationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Message messageFor(Object event) throws Exception {
        return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event)).build();
    }

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

    private CertificationExpiredEvent expiredEventFor(UUID associationId) {
        return new CertificationExpiredEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), associationId, LocalDate.now().minusDays(1));
    }

    @Test
    void aFreshExpiredEventBlocksTheAssociationAndARedeliveryOfTheSameEventDoesNotThrow() throws Exception {
        UUID associationId = UUID.randomUUID();
        CertificationExpiredEvent event = expiredEventFor(associationId);

        listener.handle(messageFor(event), "certification.expired");
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
        Instant blockedAtAfterFirstDelivery = blockedAtOf(associationId);

        // Same eventId, simulating RabbitMQ redelivery -- must be a no-op on
        // downstream state (still blocked), not just "doesn't throw".
        listener.handle(messageFor(event), "certification.expired");
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
        assertThat(blockedAtOf(associationId)).isEqualTo(blockedAtAfterFirstDelivery);
    }

    @Test
    void aFreshRenewedEventUnblocksAPreviouslyBlockedAssociation() throws Exception {
        UUID associationId = UUID.randomUUID();
        listener.handle(messageFor(expiredEventFor(associationId)), "certification.expired");
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
        CertificationRenewedEvent renewed = new CertificationRenewedEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), associationId, LocalDate.now().plusYears(1));

        listener.handle(messageFor(renewed), "certification.renewed");

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isFalse();
    }

    @Test
    void renewingAnAssociationThatWasNeverBlockedDoesNotThrow() throws Exception {
        UUID associationId = UUID.randomUUID();
        CertificationRenewedEvent renewed = new CertificationRenewedEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), associationId, LocalDate.now().plusYears(1));

        listener.handle(messageFor(renewed), "certification.renewed");

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isFalse();
    }

    @Test
    void twoDistinctExpiredEventsForTheSameAssociationRacingToBlockItForTheFirstTimeBothCompleteWithoutError()
            throws Exception {
        // Required to actually exercise the race, not just assert a happy
        // path: two DIFFERENT certifications for the same association both
        // expiring near-simultaneously produce two DISTINCT events (distinct
        // eventId, so the ledger's own PK can't short-circuit either one) --
        // both threads' block() calls can see isBlocked()==false and both
        // attempt an INSERT into blocked_association (PK = associationId).
        // This is the exact user-flagged finding from Task 35:
        // BlockedAssociationRepositoryAdapter.block()'s check-then-act is a
        // TOCTOU race. The listener's own try/catch
        // DataIntegrityViolationException (mirroring Task 30's pattern) must
        // absorb the loser's failure without it propagating as an unhandled
        // exception -- proven here with real concurrent threads, not
        // sequential calls that happen not to race.
        UUID associationId = UUID.randomUUID();
        CertificationExpiredEvent first = expiredEventFor(associationId);
        CertificationExpiredEvent second = expiredEventFor(associationId);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> handleAfterBothReady(first, readyLatch, startLatch)),
                    executor.submit(() -> handleAfterBothReady(second, readyLatch, startLatch)));
            readyLatch.await();
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
    }

    private void handleAfterBothReady(CertificationExpiredEvent event, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await();
            listener.handle(messageFor(event), "certification.expired");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}

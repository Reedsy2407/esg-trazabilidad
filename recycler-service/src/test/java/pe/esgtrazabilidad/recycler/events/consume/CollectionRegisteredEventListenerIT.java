package pe.esgtrazabilidad.recycler.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real Postgres, not mocked: proves a correctness risk mocks can't reach.
 * CollectionRegisteredEventProcessor.process() does the increment and the
 * ledger insert in one transaction, ledger insert last -- a redelivery
 * (duplicate event_id) must roll back the WHOLE transaction, including the
 * increment, not leave it half-applied. This is also the test that first
 * caught NestedTransactionNotSupportedException against the earlier design
 * (insert-first, catch-and-continue via Propagation.NESTED), which real
 * Postgres/Hibernate here doesn't support -- a mocked unit test would never
 * have surfaced that.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class CollectionRegisteredEventListenerIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CollectionRegisteredEventListener listener;

    @Autowired
    private AssociationRepository associationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BigDecimal totalKilosCollectedInDb(UUID associationId) {
        return jdbcTemplate.queryForObject(
                "SELECT total_kilos_collected FROM association WHERE id = ?", BigDecimal.class, associationId);
    }

    @Test
    void aFreshEventIncrementsTotalKilosAndARedeliveryOfTheSameEventDoesNotDoubleApplyIt() {
        Association association = associationRepository.save(Association.create(
                "Asociación evento IT", "20144444444", "REG-EVT-1", "Dirección", "a@b.pe", "999999999"));
        CollectionRegisteredEvent event = new CollectionRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                association.getId(),
                LocalDate.now(),
                new BigDecimal("15.00"));

        listener.handle(event);
        assertThat(totalKilosCollectedInDb(association.getId())).isEqualByComparingTo("15.00");

        // Same eventId, simulating RabbitMQ redelivery -- must be a no-op on
        // downstream state, not just "doesn't throw".
        listener.handle(event);
        assertThat(totalKilosCollectedInDb(association.getId())).isEqualByComparingTo("15.00");
    }

    @Test
    void concurrentEventsForTheSameAssociationBothContributeToTheTotal() throws Exception {
        // Required to FAIL under a naive load-mutate-save increment -- proves
        // AssociationJpaRepository.incrementTotalKilos's atomic UPDATE holds
        // under real concurrent threads, not just sequential calls that
        // happen not to race. A CountDownLatch forces both threads to start
        // as close to simultaneously as possible, maximizing the chance of
        // an actual race rather than accidental serialization.
        Association association = associationRepository.save(Association.create(
                "Asociación concurrente IT", "20111111111", "REG-CONC-1", "Dirección", "a@b.pe", "999999999"));
        CollectionRegisteredEvent first = new CollectionRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                association.getId(),
                LocalDate.now(),
                new BigDecimal("10.00"));
        CollectionRegisteredEvent second = new CollectionRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                association.getId(),
                LocalDate.now(),
                new BigDecimal("5.50"));

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> futures = List.of(
                    executor.submit(() -> processAfterBothReady(first, readyLatch, startLatch)),
                    executor.submit(() -> processAfterBothReady(second, readyLatch, startLatch)));
            readyLatch.await();
            startLatch.countDown();
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdown();
        }

        assertThat(totalKilosCollectedInDb(association.getId())).isEqualByComparingTo("15.50");
    }

    private void processAfterBothReady(CollectionRegisteredEvent event, CountDownLatch readyLatch, CountDownLatch startLatch) {
        try {
            readyLatch.countDown();
            startLatch.await();
            listener.handle(event);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(exception);
        }
    }
}

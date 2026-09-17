package pe.esgtrazabilidad.collection.events.outbox;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * esg.events.outbox-dispatcher.enabled=false: this task also activates the
 * real OutboxDispatcher (a live OutboxRepository bean now exists), which
 * would otherwise race this test's own save()/findPendingBatch() calls on
 * a 5s tick and try to reach the real local RabbitMQ broker -- disabled
 * here so this test stays deterministic and isolated.
 *
 * @Transactional: the Spring context (and its Testcontainers Postgres) is
 * shared across every test method in this class, so without a per-method
 * rollback, rows one test writes leak into the next test's findPendingBatch()
 * results.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
@Transactional
class OutboxEventRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OutboxRepository outboxRepository;

    @Test
    void savingAnEntryMakesItFindableInThePendingBatchAsNew() {
        OutboxEntry entry = OutboxEntry.create("TestEvent", "test.routing.key", "{\"x\":1}");

        outboxRepository.save(entry);

        List<OutboxEntry> pending = outboxRepository.findPendingBatch(10);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).id()).isEqualTo(entry.id());
        assertThat(pending.get(0).eventType()).isEqualTo("TestEvent");
        assertThat(pending.get(0).routingKey()).isEqualTo("test.routing.key");
        assertThat(pending.get(0).payloadJson()).isEqualTo("{\"x\":1}");
        assertThat(pending.get(0).status()).isEqualTo(OutboxStatus.NEW);
    }

    @Test
    void markProcessedRemovesTheEntryFromThePendingBatch() {
        OutboxEntry entry = OutboxEntry.create("TestEvent", "test.routing.key", "{}");
        outboxRepository.save(entry);

        outboxRepository.markProcessed(entry.id());

        assertThat(outboxRepository.findPendingBatch(10)).noneMatch(e -> e.id().equals(entry.id()));
    }

    @Test
    void tiedCreatedAtOrdersByIdAsATiebreaker() {
        // Task 39's ordering test relies on findPendingBatch being strictly
        // ordered -- id is UUID v7 (time-ordered, finer-grained than Instant),
        // so it's the right tiebreaker for two rows written in the same instant.
        // Deliberately saved out of id order (largerId first) so a query without
        // the tiebreaker would come back in insertion order, not id order.
        Instant sameInstant = Instant.now();
        UUID largerId = new UUID(0, 2);
        UUID smallerId = new UUID(0, 1);
        outboxRepository.save(new OutboxEntry(largerId, "EventA", "a.key", "{}", OutboxStatus.NEW, sameInstant));
        outboxRepository.save(new OutboxEntry(smallerId, "EventB", "b.key", "{}", OutboxStatus.NEW, sameInstant));

        List<OutboxEntry> pending = outboxRepository.findPendingBatch(10);

        assertThat(pending).extracting(OutboxEntry::id).containsExactly(smallerId, largerId);
    }

    @Test
    void markFailedKeepsTheEntryPendingForRetry() {
        OutboxEntry entry = OutboxEntry.create("TestEvent", "test.routing.key", "{}");
        outboxRepository.save(entry);

        outboxRepository.markFailed(entry.id());

        List<OutboxEntry> pending = outboxRepository.findPendingBatch(10);
        assertThat(pending).anySatisfy(e -> {
            assertThat(e.id()).isEqualTo(entry.id());
            assertThat(e.status()).isEqualTo(OutboxStatus.FAILED);
        });
    }
}

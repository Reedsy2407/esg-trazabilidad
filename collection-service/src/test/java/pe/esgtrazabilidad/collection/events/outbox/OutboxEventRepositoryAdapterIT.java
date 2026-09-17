package pe.esgtrazabilidad.collection.events.outbox;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
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
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
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

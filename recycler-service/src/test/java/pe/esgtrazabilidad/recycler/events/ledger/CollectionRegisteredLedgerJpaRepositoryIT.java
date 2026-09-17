package pe.esgtrazabilidad.recycler.events.ledger;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class CollectionRegisteredLedgerJpaRepositoryIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private CollectionRegisteredLedgerJpaRepository ledgerRepository;

    @Test
    void aFreshEventIdCanBeInserted() {
        UUID eventId = UUID.randomUUID();

        CollectionRegisteredLedgerEntity saved =
                ledgerRepository.save(new CollectionRegisteredLedgerEntity(eventId, Instant.now()));

        assertThat(ledgerRepository.findById(saved.getEventId())).isPresent();
    }

    @Test
    void aDuplicateEventIdViolatesThePrimaryKeyConstraint() {
        // Proves the idempotency mechanism Task 30's listener relies on: a
        // redelivered event's second insert must fail at the DB level, not
        // just be caught by an application-level existence check (which would
        // itself be TOCTOU-racy under concurrent redelivery).
        UUID eventId = UUID.randomUUID();
        ledgerRepository.saveAndFlush(new CollectionRegisteredLedgerEntity(eventId, Instant.now()));

        assertThatThrownBy(() ->
                        ledgerRepository.saveAndFlush(new CollectionRegisteredLedgerEntity(eventId, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

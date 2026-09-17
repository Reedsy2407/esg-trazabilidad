package pe.esgtrazabilidad.recycler.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
}

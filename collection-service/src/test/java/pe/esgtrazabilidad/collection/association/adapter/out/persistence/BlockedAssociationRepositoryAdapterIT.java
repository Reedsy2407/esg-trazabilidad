package pe.esgtrazabilidad.collection.association.adapter.out.persistence;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.TimeZone;
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

import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;
import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class BlockedAssociationRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BlockedAssociationRepository blockedAssociationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void blockingAnAssociationMakesItBlockedAndUnblockingRemovesIt() {
        UUID associationId = UUID.randomUUID();
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isFalse();

        blockedAssociationRepository.block(BlockedAssociation.block(associationId, Instant.now()));
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();

        blockedAssociationRepository.unblock(associationId);
        assertThat(blockedAssociationRepository.isBlocked(associationId)).isFalse();
    }

    @Test
    void unblockingAnAssociationThatWasNeverBlockedIsANoOp() {
        // Real gap this proves: JpaRepository.deleteById() throws
        // EmptyResultDataAccessException on a missing row -- a naive adapter
        // would blow up on the common case of renewing a certification that
        // never triggered a block.
        UUID associationId = UUID.randomUUID();

        blockedAssociationRepository.unblock(associationId);

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isFalse();
    }

    @Test
    void reBlockingAnAlreadyBlockedAssociationUpdatesBlockedAtInsteadOfFailingOnTheDuplicateKey() {
        UUID associationId = UUID.randomUUID();
        Instant firstBlockedAt = Instant.now().minusSeconds(60).truncatedTo(ChronoUnit.MICROS);
        Instant secondBlockedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        blockedAssociationRepository.block(BlockedAssociation.block(associationId, firstBlockedAt));

        blockedAssociationRepository.block(BlockedAssociation.block(associationId, secondBlockedAt));

        assertThat(blockedAssociationRepository.isBlocked(associationId)).isTrue();
        // Real evidence the merge() path actually ran, not just that the
        // second block() call didn't throw: the port has no getter for
        // blocked_at, so query the real column directly.
        //
        // Real gap this caught: java.sql.Timestamp.class as the requiredType
        // makes the pgjdbc driver interpret the stored "timestamp without
        // time zone" bits using the JVM's default zone (America/Lima here,
        // UTC-5), not UTC -- Hibernate wrote it as UTC, so a naive read came
        // back shifted by exactly 5 hours. Reading with an explicit UTC
        // Calendar avoids that zone reinterpretation entirely.
        Instant persistedBlockedAt = jdbcTemplate.queryForObject(
                "SELECT blocked_at FROM blocked_association WHERE association_id = ?",
                (rs, rowNum) -> rs.getTimestamp(1, Calendar.getInstance(TimeZone.getTimeZone("UTC"))).toInstant(),
                associationId);
        assertThat(persistedBlockedAt).isEqualTo(secondBlockedAt);
    }
}

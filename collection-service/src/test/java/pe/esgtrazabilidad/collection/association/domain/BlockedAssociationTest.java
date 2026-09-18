package pe.esgtrazabilidad.collection.association.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BlockedAssociationTest {

    @Test
    void blockCreatesAProjectionWithTheGivenAssociationIdAndTimestamp() {
        UUID associationId = UUID.randomUUID();
        Instant blockedAt = Instant.now();

        BlockedAssociation blocked = BlockedAssociation.block(associationId, blockedAt);

        assertThat(blocked.getAssociationId()).isEqualTo(associationId);
        assertThat(blocked.getBlockedAt()).isEqualTo(blockedAt);
    }
}

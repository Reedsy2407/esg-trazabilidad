package pe.esgtrazabilidad.kernel.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxStatusTest {

    @Test
    void hasExactlyTheThreeExpectedValues() {
        assertThat(OutboxStatus.values()).containsExactlyInAnyOrder(
                OutboxStatus.NEW, OutboxStatus.PROCESSED, OutboxStatus.FAILED);
    }
}

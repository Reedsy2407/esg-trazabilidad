package pe.esgtrazabilidad.kernel.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventPublishingStrategyTest {

    @Test
    void hasExactlyTheThreeExpectedValues() {
        assertThat(EventPublishingStrategy.values()).containsExactlyInAnyOrder(
                EventPublishingStrategy.GCP_PUB_SUB,
                EventPublishingStrategy.MOCK,
                EventPublishingStrategy.SPRING_EVENTS);
    }
}

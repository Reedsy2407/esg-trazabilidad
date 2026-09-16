package pe.esgtrazabilidad.kernel.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventPublishingStrategyTest {

    @Test
    void hasExactlyTheTwoExpectedValues() {
        assertThat(EventPublishingStrategy.values()).containsExactlyInAnyOrder(
                EventPublishingStrategy.RABBITMQ,
                EventPublishingStrategy.MOCK);
    }
}

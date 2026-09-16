package pe.esgtrazabilidad.kernel.events;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxEntryTest {

    @Test
    void createProducesANewEntryWithAGeneratedIdAndNewStatus() {
        OutboxEntry entry = OutboxEntry.create(
                "CollectionRegisteredEvent", "collection.record.registered", "{\"weightKg\":10}");

        assertThat(entry.id()).isNotNull();
        assertThat(entry.eventType()).isEqualTo("CollectionRegisteredEvent");
        assertThat(entry.routingKey()).isEqualTo("collection.record.registered");
        assertThat(entry.payloadJson()).isEqualTo("{\"weightKg\":10}");
        assertThat(entry.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(entry.createdAt()).isNotNull();
    }

    @Test
    void eachCreatedEntryGetsADistinctId() {
        OutboxEntry first = OutboxEntry.create("EventA", "a.routing.key", "{}");
        OutboxEntry second = OutboxEntry.create("EventA", "a.routing.key", "{}");

        assertThat(first.id()).isNotEqualTo(second.id());
    }
}

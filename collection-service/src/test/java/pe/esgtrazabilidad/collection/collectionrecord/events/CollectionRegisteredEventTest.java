package pe.esgtrazabilidad.collection.collectionrecord.events;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;

import static org.assertj.core.api.Assertions.assertThat;

class CollectionRegisteredEventTest {

    @Test
    void fromMapsEveryFieldFromTheRecordAndGeneratesEventMetadata() {
        CollectionRecord record = CollectionRecord.create(
                UUID.randomUUID(), null, UUID.randomUUID(), LocalDate.now(), new BigDecimal("12.5"));

        CollectionRegisteredEvent event = CollectionRegisteredEvent.from(record);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.recordId()).isEqualTo(record.getId());
        assertThat(event.neighborId()).isEqualTo(record.getNeighborId());
        assertThat(event.associationId()).isEqualTo(record.getAssociationId());
        assertThat(event.collectionDate()).isEqualTo(record.getCollectionDate());
        assertThat(event.weightKg()).isEqualTo(record.getWeightKg());
        assertThat(event.routingKey()).isEqualTo("collection.record.registered");
    }
}

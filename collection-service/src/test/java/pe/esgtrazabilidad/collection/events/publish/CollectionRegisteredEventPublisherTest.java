package pe.esgtrazabilidad.collection.events.publish;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.events.CollectionRegisteredEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectionRegisteredEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void publishWritesAnOutboxEntryReusingTheEventsOwnIdAndTimestamp() {
        CollectionRecord record = CollectionRecord.create(
                UUID.randomUUID(), null, UUID.randomUUID(), LocalDate.now(), new BigDecimal("10"));
        CollectionRegisteredEvent event = CollectionRegisteredEvent.from(record);
        CollectionRegisteredEventPublisher publisher =
                new CollectionRegisteredEventPublisher(outboxRepository, objectMapper);

        publisher.publish(event);

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEntry entry = captor.getValue();
        assertThat(entry.id()).isEqualTo(event.eventId());
        assertThat(entry.createdAt()).isEqualTo(event.occurredAt());
        assertThat(entry.eventType()).isEqualTo("CollectionRegisteredEvent");
        assertThat(entry.routingKey()).isEqualTo("collection.record.registered");
        assertThat(entry.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(entry.payloadJson())
                .contains(record.getId().toString())
                .contains(record.getNeighborId().toString())
                .contains(record.getAssociationId().toString());
    }
}

package pe.esgtrazabilidad.recycler.events.publish;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.events.RecyclerStatusChangedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecyclerStatusChangedEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void publishWritesAnOutboxEntryWithTheContractFields() throws Exception {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", UUID.randomUUID());
        recycler.deactivate();
        RecyclerStatusChangedEvent event = RecyclerStatusChangedEvent.from(recycler);

        new RecyclerStatusChangedEventPublisher(outboxRepository, objectMapper).publish(event);

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEntry entry = captor.getValue();
        assertThat(entry.id()).isEqualTo(event.eventId());
        assertThat(entry.createdAt()).isEqualTo(event.occurredAt());
        assertThat(entry.eventType()).isEqualTo("RecyclerStatusChangedEvent");
        assertThat(entry.routingKey()).isEqualTo("recycler.status.changed");
        assertThat(entry.status()).isEqualTo(OutboxStatus.NEW);
        var json = objectMapper.readTree(entry.payloadJson());
        assertThat(json.get("eventId").asText()).isEqualTo(event.eventId().toString());
        assertThat(json.get("recyclerId").asText()).isEqualTo(recycler.getId().toString());
        assertThat(json.get("associationId").asText()).isEqualTo(recycler.getAssociationId().toString());
        assertThat(json.get("status").asText()).isEqualTo("INACTIVE");
        assertThat(json.has("occurredAt")).isTrue();
    }
}

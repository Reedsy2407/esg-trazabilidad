package pe.esgtrazabilidad.recycler.events.publish;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.events.CertificationExpiredEvent;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CertificationExpiredEventPublisherTest {

    @Mock
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void publishWritesAnOutboxEntryReusingTheEventsOwnIdAndTimestamp() {
        Certification certification = Certification.reconstruct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ISO 14001",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1),
                null);
        CertificationExpiredEvent event = CertificationExpiredEvent.from(certification);
        CertificationExpiredEventPublisher publisher =
                new CertificationExpiredEventPublisher(outboxRepository, objectMapper);

        publisher.publish(event);

        ArgumentCaptor<OutboxEntry> captor = ArgumentCaptor.forClass(OutboxEntry.class);
        verify(outboxRepository).save(captor.capture());
        OutboxEntry entry = captor.getValue();
        assertThat(entry.id()).isEqualTo(event.eventId());
        assertThat(entry.createdAt()).isEqualTo(event.occurredAt());
        assertThat(entry.eventType()).isEqualTo("CertificationExpiredEvent");
        assertThat(entry.routingKey()).isEqualTo("certification.expired");
        assertThat(entry.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(entry.payloadJson())
                .contains(certification.getId().toString())
                .contains(certification.getAssociationId().toString());
    }
}

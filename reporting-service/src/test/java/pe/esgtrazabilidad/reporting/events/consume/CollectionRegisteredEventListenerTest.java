package pe.esgtrazabilidad.reporting.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectionRegisteredEventListenerTest {

    @Mock
    private TracedCollectionEntryJpaRepository repository;

    @Captor
    private ArgumentCaptor<TracedCollectionEntryEntity> entityCaptor;

    private CollectionRegisteredEvent sampleEvent() {
        return new CollectionRegisteredEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDate.now(),
                new BigDecimal("12.50"));
    }

    @Test
    void savesATracedCollectionEntryKeyedByTheEventId() {
        // TracedCollectionEntryEntity doesn't override equals()/hashCode(),
        // so verify(repository).saveAndFlush(event-shaped-matcher) can't
        // catch a field mix-up (e.g. neighborId used where associationId
        // belongs) -- an ArgumentCaptor plus explicit field assertions can.
        CollectionRegisteredEvent event = sampleEvent();

        new CollectionRegisteredEventListener(repository).handle(event);

        verify(repository).saveAndFlush(entityCaptor.capture());
        TracedCollectionEntryEntity saved = entityCaptor.getValue();
        assertThat(saved.getEventId()).isEqualTo(event.eventId());
        assertThat(saved.getAssociationId()).isEqualTo(event.associationId());
        assertThat(saved.getCollectionDate()).isEqualTo(event.collectionDate());
        assertThat(saved.getWeightKg()).isEqualByComparingTo(event.weightKg());
    }

    @Test
    void aDuplicateEventIsSwallowedWithoutRethrowing() {
        // A DataIntegrityViolationException here means the row already
        // exists (same eventId, real broker redelivery) -- this just needs
        // to ack the message (return normally), not retry it forever.
        CollectionRegisteredEvent event = sampleEvent();
        doThrow(new DataIntegrityViolationException("duplicate event_id"))
                .when(repository)
                .saveAndFlush(any(TracedCollectionEntryEntity.class));

        assertThatCode(() -> new CollectionRegisteredEventListener(repository).handle(event))
                .doesNotThrowAnyException();
    }
}

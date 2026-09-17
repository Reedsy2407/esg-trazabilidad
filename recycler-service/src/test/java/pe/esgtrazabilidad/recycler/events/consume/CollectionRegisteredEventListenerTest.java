package pe.esgtrazabilidad.recycler.events.consume;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CollectionRegisteredEventListenerTest {

    @Mock
    private CollectionRegisteredEventProcessor processor;

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
    void delegatesToTheProcessor() {
        CollectionRegisteredEvent event = sampleEvent();

        new CollectionRegisteredEventListener(processor).handle(event);

        verify(processor).process(event);
    }

    @Test
    void aDuplicateEventIsSwallowedWithoutRethrowing() {
        // A DataIntegrityViolationException here means the whole processing
        // transaction (increment included) already rolled back inside
        // processor.process() -- this just needs to ack the message (return
        // normally), not retry it forever.
        CollectionRegisteredEvent event = sampleEvent();
        doThrow(new DataIntegrityViolationException("duplicate event_id")).when(processor).process(event);

        assertThatCode(() -> new CollectionRegisteredEventListener(processor).handle(event))
                .doesNotThrowAnyException();
    }
}

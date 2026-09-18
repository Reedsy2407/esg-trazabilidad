package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CertificationStatusEventListenerTest {

    @Mock
    private CertificationStatusEventProcessor processor;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private CertificationStatusEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new CertificationStatusEventListener(processor, objectMapper);
    }

    private Message messageFor(Object event) throws Exception {
        return MessageBuilder.withBody(objectMapper.writeValueAsBytes(event)).build();
    }

    @Test
    void anExpiredEventRoutingKeyDelegatesToProcessExpired() throws Exception {
        CertificationExpiredEvent event = new CertificationExpiredEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());

        listener.handle(messageFor(event), "certification.expired");

        verify(processor).processExpired(event);
    }

    @Test
    void aRenewedEventRoutingKeyDelegatesToProcessRenewed() throws Exception {
        CertificationRenewedEvent event = new CertificationRenewedEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now().plusYears(1));

        listener.handle(messageFor(event), "certification.renewed");

        verify(processor).processRenewed(event);
    }

    @Test
    void aDuplicateExpiredEventIsSwallowedWithoutRethrowing() throws Exception {
        // A DataIntegrityViolationException here means the whole processing
        // transaction (block write included) already rolled back inside
        // processor.processExpired() -- this just needs to ack the message
        // (return normally), not retry it forever.
        CertificationExpiredEvent event = new CertificationExpiredEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());
        doThrow(new DataIntegrityViolationException("duplicate event_id")).when(processor).processExpired(event);

        assertThatCode(() -> listener.handle(messageFor(event), "certification.expired"))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnrecognizedRoutingKeyIsIgnored() throws Exception {
        CertificationExpiredEvent event = new CertificationExpiredEvent(
                UUID.randomUUID(), Instant.now(), UUID.randomUUID(), UUID.randomUUID(), LocalDate.now());

        listener.handle(messageFor(event), "some.other.key");

        verifyNoInteractions(processor);
    }
}

package pe.esgtrazabilidad.kernel.amqp;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxDispatcherTest {

    private static final TopicExchange EXCHANGE = new TopicExchange("esg-trazabilidad.events");

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void dispatchesAllPendingRowsInOrderAndMarksEachProcessed() {
        OutboxEntry first = OutboxEntry.create("EventA", "a.routing.key", "{\"a\":1}");
        OutboxEntry second = OutboxEntry.create("EventB", "b.routing.key", "{\"b\":2}");
        when(outboxRepository.findPendingBatch(anyInt())).thenReturn(List.of(first, second));

        new OutboxDispatcher(outboxRepository, rabbitTemplate, EXCHANGE).dispatchPending();

        InOrder inOrder = inOrder(rabbitTemplate, outboxRepository);
        inOrder.verify(rabbitTemplate).convertAndSend(EXCHANGE.getName(), first.routingKey(), first.payloadJson());
        inOrder.verify(outboxRepository).markProcessed(first.id());
        inOrder.verify(rabbitTemplate).convertAndSend(EXCHANGE.getName(), second.routingKey(), second.payloadJson());
        inOrder.verify(outboxRepository).markProcessed(second.id());
    }

    @Test
    void marksAFailedEntryFailedWithoutRethrowing() {
        OutboxEntry entry = OutboxEntry.create("EventA", "a.routing.key", "{}");
        when(outboxRepository.findPendingBatch(anyInt())).thenReturn(List.of(entry));
        doThrow(new AmqpException("broker unreachable"))
                .when(rabbitTemplate)
                .convertAndSend(any(String.class), any(String.class), any(Object.class));

        new OutboxDispatcher(outboxRepository, rabbitTemplate, EXCHANGE).dispatchPending();

        verify(outboxRepository).markFailed(entry.id());
        verify(outboxRepository, never()).markProcessed(entry.id());
    }

    @Test
    void oneEntryFailingDoesNotStopTheRestOfTheBatch() {
        OutboxEntry failing = OutboxEntry.create("EventA", "a.routing.key", "{}");
        OutboxEntry healthy = OutboxEntry.create("EventB", "b.routing.key", "{}");
        when(outboxRepository.findPendingBatch(anyInt())).thenReturn(List.of(failing, healthy));
        doThrow(new AmqpException("broker unreachable"))
                .when(rabbitTemplate)
                .convertAndSend(EXCHANGE.getName(), failing.routingKey(), failing.payloadJson());

        new OutboxDispatcher(outboxRepository, rabbitTemplate, EXCHANGE).dispatchPending();

        verify(outboxRepository).markFailed(failing.id());
        verify(rabbitTemplate).convertAndSend(EXCHANGE.getName(), healthy.routingKey(), healthy.payloadJson());
        verify(outboxRepository).markProcessed(healthy.id());
    }

    @Test
    void doesNothingWhenThereAreNoPendingRows() {
        when(outboxRepository.findPendingBatch(anyInt())).thenReturn(List.of());

        new OutboxDispatcher(outboxRepository, rabbitTemplate, EXCHANGE).dispatchPending();

        verifyNoInteractions(rabbitTemplate);
        verify(outboxRepository, never()).markProcessed(any());
        verify(outboxRepository, never()).markFailed(any());
    }
}

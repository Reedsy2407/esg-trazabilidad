package pe.esgtrazabilidad.kernel.amqp;

import java.util.List;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;

/**
 * Generic: works against whichever {@link OutboxRepository} bean is present
 * in a given service's context, so this class is written and tested once
 * here and never duplicated per service. Only activates once that service
 * has its own outbox adapter wired up ({@code @ConditionalOnBean}) -- until
 * then this bean simply isn't created, so adding shared-kernel's AMQP/
 * ShedLock dependencies transitively never breaks a service that hasn't
 * built its own outbox yet.
 *
 * <p>A publish failure marks the row FAILED and moves on to the next one --
 * never rethrown, never silently dropped either: a later run picks FAILED
 * rows back up the same way it picks up NEW ones, since {@link
 * OutboxRepository#findPendingBatch} is each adapter's own concern to
 * define "pending" for.
 */
@Component
@ConditionalOnBean(OutboxRepository.class)
@ConditionalOnProperty(prefix = "esg.events.outbox-dispatcher", name = "enabled", matchIfMissing = true)
public class OutboxDispatcher {

    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchangeName;

    public OutboxDispatcher(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate, TopicExchange exchange) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchangeName = exchange.getName();
    }

    @Scheduled(fixedDelayString = "${esg.events.outbox-dispatcher.interval-ms:5000}")
    @SchedulerLock(name = "outboxDispatcher", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void dispatchPending() {
        List<OutboxEntry> pending = outboxRepository.findPendingBatch(BATCH_SIZE);
        for (OutboxEntry entry : pending) {
            try {
                rabbitTemplate.convertAndSend(exchangeName, entry.routingKey(), entry.payloadJson());
                outboxRepository.markProcessed(entry.id());
            } catch (RuntimeException exception) {
                outboxRepository.markFailed(entry.id());
            }
        }
    }
}

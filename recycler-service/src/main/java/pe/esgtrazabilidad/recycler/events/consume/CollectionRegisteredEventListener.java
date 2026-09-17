package pe.esgtrazabilidad.recycler.events.consume;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
class CollectionRegisteredEventListener {

    private final CollectionRegisteredEventProcessor processor;

    CollectionRegisteredEventListener(CollectionRegisteredEventProcessor processor) {
        this.processor = processor;
    }

    @RabbitListener(queues = CollectionRegisteredQueueConfig.QUEUE_NAME)
    void handle(CollectionRegisteredEvent event) {
        try {
            processor.process(event);
        } catch (DataIntegrityViolationException exception) {
            // Already processed -- processor.process()'s own transaction
            // (increment included) already rolled back; ack and move on,
            // not an error.
        }
    }
}

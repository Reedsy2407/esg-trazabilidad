package pe.esgtrazabilidad.reporting.events.consume;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares this service's own durable queue bound to the shared topic
 * exchange (declared once in shared-kernel's RabbitTopologyConfig, injected
 * here rather than redeclared by name) -- matches the topology described in
 * SPEC-cross-service-events.md: one exchange, each consumer owns its own
 * queue+binding. Zero change needed to collection-service's own publisher
 * -- exactly the "adding reporting-service as a future consumer" scenario
 * that topology was designed to support.
 */
@Configuration
class CollectionRegisteredQueueConfig {

    static final String QUEUE_NAME = "collection.registered.reporting-service";
    private static final String ROUTING_KEY = "collection.record.registered";

    @Bean
    Queue collectionRegisteredQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    Binding collectionRegisteredBinding(Queue collectionRegisteredQueue, TopicExchange esgTrazabilidadEventsExchange) {
        return BindingBuilder.bind(collectionRegisteredQueue).to(esgTrazabilidadEventsExchange).with(ROUTING_KEY);
    }
}

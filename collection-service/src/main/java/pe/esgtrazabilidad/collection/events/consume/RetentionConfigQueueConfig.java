package pe.esgtrazabilidad.collection.events.consume;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A durable queue bound to auth.retention.changed: events wait here while the service sleeps. */
@Configuration
class RetentionConfigQueueConfig {

    static final String QUEUE_NAME = "auth.retention.collection-service";
    static final String ROUTING_KEY = "auth.retention.changed";

    @Bean
    Queue retentionConfigQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    Binding retentionConfigBinding(Queue retentionConfigQueue, TopicExchange esgTrazabilidadEventsExchange) {
        return BindingBuilder.bind(retentionConfigQueue).to(esgTrazabilidadEventsExchange).with(ROUTING_KEY);
    }
}

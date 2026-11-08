package pe.esgtrazabilidad.collection.events.consume;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** A durable queue bound to recycler.status.changed: events wait here while the service sleeps. */
@Configuration
class RecyclerStatusQueueConfig {

    static final String QUEUE_NAME = "recycler.status.collection-service";
    static final String ROUTING_KEY = "recycler.status.changed";

    @Bean
    Queue recyclerStatusQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    Binding recyclerStatusBinding(Queue recyclerStatusQueue, TopicExchange esgTrazabilidadEventsExchange) {
        return BindingBuilder.bind(recyclerStatusQueue).to(esgTrazabilidadEventsExchange).with(ROUTING_KEY);
    }
}

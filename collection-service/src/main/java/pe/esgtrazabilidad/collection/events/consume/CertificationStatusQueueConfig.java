package pe.esgtrazabilidad.collection.events.consume;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single durable queue bound to BOTH certification.expired and
 * certification.renewed -- CertificationStatusEventListener handles both
 * event types on the one queue (see SPEC-cross-service-events.md's Project
 * Structure), unlike Direction A's CollectionRegisteredQueueConfig (Task 30)
 * which only ever needed one binding.
 */
@Configuration
class CertificationStatusQueueConfig {

    static final String QUEUE_NAME = "certification.status.collection-service";
    private static final String EXPIRED_ROUTING_KEY = "certification.expired";
    private static final String RENEWED_ROUTING_KEY = "certification.renewed";

    @Bean
    Queue certificationStatusQueue() {
        return new Queue(QUEUE_NAME, true);
    }

    @Bean
    Binding certificationExpiredBinding(Queue certificationStatusQueue, TopicExchange esgTrazabilidadEventsExchange) {
        return BindingBuilder.bind(certificationStatusQueue).to(esgTrazabilidadEventsExchange).with(EXPIRED_ROUTING_KEY);
    }

    @Bean
    Binding certificationRenewedBinding(Queue certificationStatusQueue, TopicExchange esgTrazabilidadEventsExchange) {
        return BindingBuilder.bind(certificationStatusQueue).to(esgTrazabilidadEventsExchange).with(RENEWED_ROUTING_KEY);
    }
}

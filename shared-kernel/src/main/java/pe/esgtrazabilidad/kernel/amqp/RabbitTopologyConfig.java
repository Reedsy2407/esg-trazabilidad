package pe.esgtrazabilidad.kernel.amqp;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * One shared topic exchange for every domain event this project publishes,
 * routing keys namespaced by event type (e.g. collection.record.registered,
 * certification.expired). Declared here so both services get it for free by
 * depending on shared-kernel -- no duplicated declaration, and a future
 * consumer (reporting-service) only needs its own queue bound to this same
 * exchange, no publisher change.
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EVENTS_EXCHANGE = "esg-trazabilidad.events";

    @Bean
    public TopicExchange esgTrazabilidadEventsExchange() {
        return new TopicExchange(EVENTS_EXCHANGE, true, false);
    }
}

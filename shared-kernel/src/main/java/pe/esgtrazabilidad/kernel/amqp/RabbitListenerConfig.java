package pe.esgtrazabilidad.kernel.amqp;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A dedicated container factory for @RabbitListener methods that take a
 * typed payload (e.g. a DomainEvent record), not a global MessageConverter
 * bean. A global MessageConverter bean would ALSO become RabbitTemplate's
 * default converter (Spring Boot auto-configures RabbitTemplate to pick up
 * any single MessageConverter bean it finds) -- breaking OutboxDispatcher's
 * publish side, which sends OutboxEntry.payloadJson() as an already-
 * serialized JSON String via the default SimpleMessageConverter. Wrapping a
 * Jackson2JsonMessageConverter as a JSON bean (not returned/exposed as its
 * own @Bean) keeps it invisible to that auto-detection, scoped only to
 * listeners that explicitly opt in via
 * {@code @RabbitListener(containerFactory = "jsonRabbitListenerContainerFactory")}.
 */
@Configuration
public class RabbitListenerConfig {

    @Bean
    public SimpleRabbitListenerContainerFactory jsonRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(new Jackson2JsonMessageConverter(objectMapper));
        return factory;
    }
}

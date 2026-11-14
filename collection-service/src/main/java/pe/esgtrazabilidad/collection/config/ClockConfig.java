package pe.esgtrazabilidad.collection.config;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The service's only source of "now": UTC instants. Anything that depends on time (assignment
 * timestamps, retention cut-offs, the Lima date of a collection) reads this clock, so tests can
 * replace it with a fixed one instead of sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.systemUTC();
    }
}

package pe.esgtrazabilidad.kernel.observability;

import javax.sql.DataSource;

import jakarta.servlet.Filter;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * Registered in AutoConfiguration.imports. The request line (service, method, route template,
 * status, durationMs, dbMs) and the slow-query line; see {@link RequestTimingFilter}. The slow-query
 * threshold is {@code esg.observability.slow-query-ms} (150 by default).
 */
@AutoConfiguration
@ConditionalOnClass({Filter.class, DataSource.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ObservabilityAutoConfiguration {

    @Bean
    RequestTimingFilter requestTimingFilter(Environment environment) {
        return new RequestTimingFilter(environment.getProperty("spring.application.name", "unknown"));
    }

    @Bean
    static BeanPostProcessor timedDataSourcePostProcessor(Environment environment) {
        String service = environment.getProperty("spring.application.name", "unknown");
        long slow = environment.getProperty("esg.observability.slow-query-ms", Long.class, 150L);
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof DataSource dataSource && !TimedDataSource.isWrapped(dataSource)
                        ? TimedDataSource.wrap(dataSource, service, slow)
                        : bean;
            }
        };
    }
}

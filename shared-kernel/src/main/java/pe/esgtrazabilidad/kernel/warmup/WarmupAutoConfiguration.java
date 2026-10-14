package pe.esgtrazabilidad.kernel.warmup;

import java.util.List;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Registered in AutoConfiguration.imports; see {@link StartupWarmup}. */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "esg.warmup.enabled", havingValue = "true")
public class WarmupAutoConfiguration {

    @Bean
    StartupWarmup startupWarmup(Environment environment, List<WarmupTask> tasks) {
        return new StartupWarmup(environment, tasks);
    }
}

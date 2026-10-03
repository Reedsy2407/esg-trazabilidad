package pe.esgtrazabilidad.kernel.info;

import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Registered in AutoConfiguration.imports; inert in a service without the actuator. */
@AutoConfiguration
@ConditionalOnClass(InfoContributor.class)
public class DeployedCommitInfoAutoConfiguration {

    @Bean
    DeployedCommitInfoContributor deployedCommitInfoContributor(Environment environment) {
        return new DeployedCommitInfoContributor(environment);
    }
}

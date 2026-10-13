package pe.esgtrazabilidad.reporting.it.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Picked up by component scanning in every @SpringBootTest of this module (it
 * lives under the application's base package in the test source set), so the
 * real provider is never the one the services use in a test. The fake is a
 * RucLookup, so being primary it wins over the configured adapter.
 */
@Configuration
public class FakeRucLookupConfig {

    @Bean
    @Primary
    public FakeRucLookup fakeRucLookup() {
        return new FakeRucLookup();
    }
}

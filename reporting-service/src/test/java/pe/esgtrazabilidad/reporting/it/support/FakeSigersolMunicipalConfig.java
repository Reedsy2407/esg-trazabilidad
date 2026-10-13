package pe.esgtrazabilidad.reporting.it.support;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Replaces the configured SIGERSOL adapter in every @SpringBootTest of this module. */
@Configuration
public class FakeSigersolMunicipalConfig {

    @Bean
    @Primary
    public FakeSigersolMunicipal fakeSigersolMunicipal() {
        return new FakeSigersolMunicipal();
    }
}

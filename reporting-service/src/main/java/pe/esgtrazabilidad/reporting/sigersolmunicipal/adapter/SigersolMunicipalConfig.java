package pe.esgtrazabilidad.reporting.sigersolmunicipal.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;

/**
 * Chooses the SIGERSOL Municipal provider. {@code SIGERSOL_MUNICIPAL_PROVIDER} has no default, so
 * the service does not start without saying what it does. {@code none} answers "unavailable" for
 * every district: nothing in the service depends on SIGERSOL, so unlike the SUNAT lookup it is
 * allowed everywhere.
 */
@Configuration
class SigersolMunicipalConfig {

    @Bean
    SigersolMunicipalStats sigersolMunicipalStats(Environment environment) {
        String provider = environment.getRequiredProperty("SIGERSOL_MUNICIPAL_PROVIDER");
        return switch (provider) {
            case "none" -> new UnavailableSigersolMunicipal();
            default -> throw new IllegalStateException("SIGERSOL_MUNICIPAL_PROVIDER must be none, got " + provider);
        };
    }
}

package pe.esgtrazabilidad.reporting.sunat.adapter;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import pe.esgtrazabilidad.reporting.sunat.RucLookup;

/**
 * Chooses the provider. {@code RUC_LOOKUP_PROVIDER} has no default, so the
 * service does not start without it. {@code none} (every lookup unavailable) is
 * for development and tests only and is refused when {@code RENDER=true}. The
 * Decolecta token comes from {@code RUC_LOOKUP_TOKEN} (or {@code DECOLECTA_TOKEN});
 * there is no default, and a missing token fails startup.
 */
@Configuration
class RucLookupConfig {

    private static final String DEFAULT_BASE_URL = "https://api.decolecta.com";

    @Bean
    RucLookup rucLookup(Environment environment) {
        String provider = environment.getRequiredProperty("RUC_LOOKUP_PROVIDER");
        return switch (provider) {
            case "decolecta" -> new DecolectaRucLookup(
                    environment.getProperty("RUC_LOOKUP_BASE_URL", DEFAULT_BASE_URL),
                    requiredToken(environment),
                    Duration.ofSeconds(3),
                    Duration.ofSeconds(5));
            case "none" -> {
                if ("true".equalsIgnoreCase(environment.getProperty("RENDER"))) {
                    throw new IllegalStateException(
                            "RUC_LOOKUP_PROVIDER=none is for development and tests only: refused when RENDER=true");
                }
                yield new UnavailableRucLookup();
            }
            default -> throw new IllegalStateException(
                    "RUC_LOOKUP_PROVIDER must be decolecta or none, got " + provider);
        };
    }

    private static String requiredToken(Environment environment) {
        String token = environment.getProperty("RUC_LOOKUP_TOKEN");
        if (token == null || token.isBlank()) {
            token = environment.getProperty("DECOLECTA_TOKEN");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "RUC_LOOKUP_PROVIDER=decolecta needs RUC_LOOKUP_TOKEN (or DECOLECTA_TOKEN)");
        }
        return token;
    }
}

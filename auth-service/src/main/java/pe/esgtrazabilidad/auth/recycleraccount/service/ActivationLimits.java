package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * R4 limits of the public activation route. The defaults are the decided ones: 5 failed attempts per
 * code, after which that code is blocked for 15 minutes, and 20 attempts per client address in 15
 * minutes. Not secrets; production does not set them, ITs lower them by property.
 */
public record ActivationLimits(int maxFailedPerCode, Duration codeBlock, int maxPerAddress, Duration addressWindow) {

    public ActivationLimits {
        if (maxFailedPerCode < 1 || maxPerAddress < 1 || codeBlock.isZero() || addressWindow.isZero()) {
            throw new IllegalArgumentException("Activation limits must be positive");
        }
    }

    @Configuration
    static class Config {

        @Bean
        ActivationLimits activationLimits(
                @Value("${esg.auth.recycler-activation.max-failed-attempts-per-code:5}") int maxFailedPerCode,
                @Value("${esg.auth.recycler-activation.code-block-minutes:15}") long codeBlockMinutes,
                @Value("${esg.auth.recycler-activation.max-attempts-per-address:20}") int maxPerAddress,
                @Value("${esg.auth.recycler-activation.address-window-minutes:15}") long addressWindowMinutes) {
            return new ActivationLimits(
                    maxFailedPerCode,
                    Duration.ofMinutes(codeBlockMinutes),
                    maxPerAddress,
                    Duration.ofMinutes(addressWindowMinutes));
        }
    }
}

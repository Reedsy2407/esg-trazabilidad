package pe.esgtrazabilidad.auth.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    // PROVISIONAL (SPEC-deployment.md Open Questions): which header carries the
    // caller's IP on Render, and how many proxy hops append to it, is confirmed
    // against a real deployed request in Task 82 before the limiter is trusted.
    static final String CLIENT_IP_HEADER = "X-Forwarded-For";
    static final int TRUSTED_PROXY_HOPS = 1;

    /**
     * Not a secret, and production never sets it: the default is the spec's
     * 5 attempts per minute. It exists only so src/test/resources can raise it
     * for the ITs that log in many times from 127.0.0.1; LoginRateLimitIT pins
     * it back to 5.
     */
    @Bean
    LoginRateLimiter loginRateLimiter(
            @Value("${esg.auth.login-rate-limit.capacity:" + LoginRateLimiter.DEFAULT_CAPACITY + "}") int capacity) {
        return new LoginRateLimiter(capacity);
    }

    @Bean
    ClientIpResolver clientIpResolver() {
        return new ClientIpResolver(CLIENT_IP_HEADER, TRUSTED_PROXY_HOPS);
    }
}

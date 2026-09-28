package pe.esgtrazabilidad.auth.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    // Confirmed against real requests on Render (Task 82): its Cloudflare edge
    // appends the caller's address to the right of X-Forwarded-For and keeps
    // any caller-sent entries to its left, so exactly one hop is trusted.
    // remoteAddr is a Cloudflare edge address that varies per request.
    // CF-Connecting-IP / True-Client-IP are deliberately not read: nothing
    // verified that the edge overwrites a caller-sent one, and they are
    // specific to Render's current CDN, while every proxy appends to XFF.
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

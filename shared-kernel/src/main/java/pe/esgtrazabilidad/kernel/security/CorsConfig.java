package pe.esgtrazabilidad.kernel.security;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * The one CORS policy for every service, applied through each service's
 * SecurityFilterChain with http.cors(). Spring Security's CorsFilter then
 * runs before authentication and before auth-service's LoginRateLimitFilter,
 * so a preflight never gets a 401, and 401/403/429 error bodies still carry
 * the CORS headers a browser needs to read them. Imported through
 * JwtSecurityConfig, which every service already imports.
 *
 * <p>Allowed origins come from CORS_ALLOWED_ORIGINS, comma separated. Empty
 * (the default) allows no origin at all, so services already deployed
 * without the variable keep starting. "*" or anything that isn't a bare
 * scheme://host[:port] fails startup instead of being silently ignored.
 */
@Configuration
public class CorsConfig {

    static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    static final List<String> ALLOWED_HEADERS =
            List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT);
    // Retry-After for the login 429, Content-Disposition for the PDF/CSV
    // downloads' file name.
    static final List<String> EXPOSED_HEADERS = List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.CONTENT_DISPOSITION);
    static final long MAX_AGE_SECONDS = 3600;

    private static final Pattern ORIGIN = Pattern.compile("https?://[^/\\s?#*]+");

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${CORS_ALLOWED_ORIGINS:}") String allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(parseAllowedOrigins(allowedOrigins));
        config.setAllowedMethods(ALLOWED_METHODS);
        config.setAllowedHeaders(ALLOWED_HEADERS);
        config.setExposedHeaders(EXPOSED_HEADERS);
        config.setAllowCredentials(false); // the JWT travels in a header, never a cookie
        config.setMaxAge(MAX_AGE_SECONDS);
        return new SameHostExemptSource(config);
    }

    static List<String> parseAllowedOrigins(String raw) {
        List<String> origins = new ArrayList<>();
        for (String entry : raw.split(",")) {
            String origin = entry.trim();
            if (origin.isEmpty()) {
                continue;
            }
            if (origin.contains("*")) {
                throw new IllegalStateException(
                        "CORS_ALLOWED_ORIGINS must list explicit origins; '*' is not allowed: " + origin);
            }
            if (!ORIGIN.matcher(origin).matches()) {
                throw new IllegalStateException("CORS_ALLOWED_ORIGINS entries must be scheme://host[:port] "
                        + "with no path or trailing slash: " + origin);
            }
            origins.add(origin);
        }
        return List.copyOf(origins);
    }

    /**
     * A request whose Origin names the very host it was sent to (e.g. the
     * service's own Swagger UI posting to its own API) is same-site, not
     * cross-origin, and gets no CORS processing at all, exactly as before
     * CORS existed. This can't be left to Spring: behind Render's
     * TLS-terminating proxy the service sees http on an internal port, so
     * Spring would treat https://<service>.onrender.com as a foreign origin
     * and reject it with 403. Host and port are compared as written, so
     * http://localhost:4200 calling localhost:8081 still goes through CORS.
     */
    static final class SameHostExemptSource implements CorsConfigurationSource {

        private final CorsConfiguration config;

        SameHostExemptSource(CorsConfiguration config) {
            this.config = config;
        }

        @Override
        public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
            return isSameHost(request) ? null : config;
        }

        private static boolean isSameHost(HttpServletRequest request) {
            String origin = request.getHeader(HttpHeaders.ORIGIN);
            String host = request.getHeader(HttpHeaders.HOST);
            if (origin == null || host == null) {
                return false;
            }
            try {
                String authority = URI.create(origin).getRawAuthority();
                return authority != null && authority.equalsIgnoreCase(host);
            } catch (IllegalArgumentException malformed) {
                return false;
            }
        }
    }
}

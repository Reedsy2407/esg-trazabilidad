package pe.esgtrazabilidad.collection.config;

import static org.springframework.security.config.Customizer.withDefaults;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import pe.esgtrazabilidad.kernel.security.FieldAccessFilter;
import pe.esgtrazabilidad.kernel.security.JwtSecurityConfig;

/**
 * No login endpoint here -- tokens are issued only by auth-service and
 * validated offline (no synchronous call back to it), same
 * stateless-JWT design as auth-service's own SecurityConfig.
 */
@Configuration
@EnableWebSecurity
@Import(JwtSecurityConfig.class)
public class SecurityConfig {

    /** Every route the recycler's device uses. */
    private static final RequestMatcher FIELD_ROUTES = new OrRequestMatcher(
            new AntPathRequestMatcher("/me/**"),
            new AntPathRequestMatcher("/workdays/**"),
            new AntPathRequestMatcher("/evidence/**"));

    /** The routes that are GPS by nature: they also need `lc=true`. */
    private static final RequestMatcher LOCATION_ROUTES = new AntPathRequestMatcher("/workdays/*/points");

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationEntryPoint jwtAuthenticationEntryPoint,
            AccessDeniedHandler jwtAccessDeniedHandler,
            ObjectMapper objectMapper)
            throws Exception {
        return http.csrf(CsrfConfigurer::disable) // stateless JWT API, no cookies/session -- CSRF doesn't apply
                // shared-kernel's CorsConfig: runs before authentication, so preflights
                // never get a 401 and error responses still carry the CORS headers.
                .cors(withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Consent gate of the field routes (A.1): a RECYCLER without `cv` gets 409 AUTH-019;
                // on the GPS-points route also without `lc=true`. A check-in that carries
                // coordinates checks the location consent itself (the body decides).
                .addFilterAfter(
                        new FieldAccessFilter(objectMapper, FIELD_ROUTES, LOCATION_ROUTES),
                        BearerTokenAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                // springdoc's documented entry point; it 302s into /swagger-ui/index.html.
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                // Render's health check. Exact path on purpose: readiness and the
                                // rest of /actuator/** stay behind a token.
                                "/actuator/health/liveness",
                                // Spring Boot forwards internally to /error to render a 404/500
                                // response when no handler matches -- that forward passes through
                                // this same filter chain again, so /error must be permitAll too or
                                // the error page's own rendering gets blocked, masking the real
                                // status (see auth-service's own SecurityConfig, where this was
                                // first found and fixed).
                                "/error")
                        .permitAll()
                        // The deployed commit only (scripts/verify-deploy.sh). Exact path and
                        // GET only, like liveness: the rest of /actuator/** stays behind a token.
                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                        .permitAll()
                        // The catalogue is read by staff and by the field app (check-in form).
                        .requestMatchers(HttpMethod.GET, "/waste-types")
                        .hasAnyRole("STAFF", "ADMIN", "RECYCLER")
                        // The retention run deletes data: ADMIN only (STAFF and RECYCLER get 403 AUTH-000).
                        .requestMatchers(HttpMethod.POST, "/retention/run")
                        .hasRole("ADMIN")
                        // Field routes: only the recycler's device. Staff and admin get 403 AUTH-000.
                        .requestMatchers("/me/**", "/workdays/**", "/evidence/**")
                        .hasRole("RECYCLER")
                        .anyRequest()
                        .hasAnyRole("STAFF", "ADMIN"))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                // Tokens that are present but invalid (bad signature, expired) are
                // rejected by the bearer filter, which uses the resource server's own
                // entry point, not exceptionHandling()'s -- without this they get an
                // empty 401 instead of the AUTH-000 body SPEC-auth-service.md defines
                // for missing, invalid and expired tokens alike.
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()).authenticationEntryPoint(jwtAuthenticationEntryPoint))
                .build();
    }
}

package pe.esgtrazabilidad.auth.config;

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
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import pe.esgtrazabilidad.auth.ratelimit.ClientIpResolver;
import pe.esgtrazabilidad.auth.ratelimit.LoginRateLimitFilter;
import pe.esgtrazabilidad.auth.ratelimit.LoginRateLimiter;
import pe.esgtrazabilidad.kernel.security.JwtSecurityConfig;

/**
 * /auth/login is the only public business endpoint in this service --
 * everything else (including POST /auth/staff-users and GET /auth/me,
 * added in Tasks 64/65) requires a valid token, same as every other
 * service's own retrofitted SecurityConfig (Tasks 66-68). /auth/login is
 * rate limited per client IP by LoginRateLimitFilter (SPEC-deployment.md).
 */
@Configuration
@EnableWebSecurity
@Import(JwtSecurityConfig.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            AuthenticationEntryPoint jwtAuthenticationEntryPoint,
            AccessDeniedHandler jwtAccessDeniedHandler,
            LoginRateLimiter loginRateLimiter,
            ClientIpResolver clientIpResolver,
            ObjectMapper objectMapper)
            throws Exception {
        return http.csrf(CsrfConfigurer::disable) // stateless JWT API, no cookies/session -- CSRF doesn't apply
                .addFilterBefore(
                        new LoginRateLimitFilter(loginRateLimiter, clientIpResolver, objectMapper),
                        UsernamePasswordAuthenticationFilter.class)
                // shared-kernel's CorsConfig: runs before authentication (and before
                // auth-service's login rate limiter), so preflights never get a 401 and
                // error responses still carry the CORS headers.
                .cors(withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                "/auth/login",
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
                                // status (e.g. an intended 404 turning into a 401).
                                "/error")
                        .permitAll()
                        // The deployed commit only (scripts/verify-deploy.sh). Exact path and
                        // GET only, like liveness: the rest of /actuator/** stays behind a token.
                        .requestMatchers(HttpMethod.GET, "/actuator/info")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
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

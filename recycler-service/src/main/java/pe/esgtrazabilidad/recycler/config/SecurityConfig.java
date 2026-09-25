package pe.esgtrazabilidad.recycler.config;

import static org.springframework.security.config.Customizer.withDefaults;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

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

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http, AuthenticationEntryPoint jwtAuthenticationEntryPoint, AccessDeniedHandler jwtAccessDeniedHandler)
            throws Exception {
        return http.csrf(CsrfConfigurer::disable) // stateless JWT API, no cookies/session -- CSRF doesn't apply
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers(
                                // springdoc's documented entry point; it 302s into /swagger-ui/index.html.
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                // Spring Boot forwards internally to /error to render a 404/500
                                // response when no handler matches -- that forward passes through
                                // this same filter chain again, so /error must be permitAll too or
                                // the error page's own rendering gets blocked, masking the real
                                // status (see auth-service's own SecurityConfig, where this was
                                // first found and fixed).
                                "/error")
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

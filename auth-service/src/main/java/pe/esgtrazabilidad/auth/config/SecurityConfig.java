package pe.esgtrazabilidad.auth.config;

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
 * /auth/login is the only public business endpoint in this service --
 * everything else (including POST /auth/staff-users and GET /auth/me,
 * added in Tasks 64/65) requires a valid token, same as every other
 * service's own retrofitted SecurityConfig (Tasks 66-68).
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
                                "/auth/login",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/health",
                                // Spring Boot forwards internally to /error to render a 404/500
                                // response when no handler matches -- that forward passes through
                                // this same filter chain again, so /error must be permitAll too or
                                // the error page's own rendering gets blocked, masking the real
                                // status (e.g. an intended 404 turning into a 401).
                                "/error")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
                .build();
    }
}

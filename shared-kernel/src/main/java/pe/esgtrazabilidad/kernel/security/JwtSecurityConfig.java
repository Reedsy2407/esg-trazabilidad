package pe.esgtrazabilidad.kernel.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Deliberately NOT registered in
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * (unlike GlobalExceptionHandler/RabbitTopologyConfig/OutboxDispatcher) --
 * that would apply it to every shared-kernel consumer automatically,
 * including recycler-service/collection-service/reporting-service before
 * their own retrofit tasks (66-68) land. The moment a JwtDecoder bean
 * exists anywhere in a service's context, Spring Boot's own
 * OAuth2ResourceServerAutoConfiguration installs a DEFAULT SecurityFilterChain
 * that requires authentication on every request -- confirmed the hard way
 * (a @WebMvcTest against recycler-service started getting 403 instead of
 * its expected status the moment this was tried as a blanket auto-config).
 * Each service that wants JWT support imports this class explicitly
 * (@Import(JwtSecurityConfig.class) on its own SecurityConfig), at the same
 * time it adds its own SecurityFilterChain bean -- so Spring Boot's default
 * never gets a chance to apply.
 *
 * <p>Offline, in-process JWT signature verification only, never a
 * synchronous call to auth-service. HS256 with a shared secret: see
 * SPEC-auth-service.md's Resolved Decisions for why this was chosen over
 * RS256 at this MVP's scale.
 *
 * <p>The entry point/access denied handler exist because Spring Security's
 * filter chain runs before Spring MVC's DispatcherServlet -- a
 * @RestControllerAdvice like GlobalExceptionHandler never sees a 401/403
 * that never reaches a controller. Both write a small JSON object by hand
 * (code/detail/status/title) rather than serializing a real
 * org.springframework.http.ProblemDetail: the raw ObjectMapper bean used
 * here isn't guaranteed to carry the same ProblemDetail Jackson mixin
 * Spring MVC's own message converter registers internally, so hand-building
 * the same field names is the more predictable choice at this filter layer.
 */
@Configuration
public class JwtSecurityConfig {

    @Bean
    JwtDecoder jwtDecoder(Environment environment) {
        String secret = environment.getRequiredProperty("JWT_SECRET");
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    AuthenticationEntryPoint jwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeSecurityError(response, objectMapper, HttpStatus.UNAUTHORIZED, "AUTH-000", "Token faltante o inválido");
    }

    @Bean
    AccessDeniedHandler jwtAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, exception) ->
                writeSecurityError(response, objectMapper, HttpStatus.FORBIDDEN, "AUTH-000", "No tienes permiso para esta acción");
    }

    private static void writeSecurityError(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, String code, String detail)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("code", code);
        objectMapper.writeValue(response.getWriter(), body);
    }
}

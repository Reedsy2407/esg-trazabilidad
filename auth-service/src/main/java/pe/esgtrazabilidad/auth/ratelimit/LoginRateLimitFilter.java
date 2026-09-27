package pe.esgtrazabilidad.auth.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import pe.esgtrazabilidad.auth.exception.AuthErrors;

/**
 * Applies LoginRateLimiter to POST /auth/login only; every other request
 * passes straight through. It runs inside the security filter chain, before
 * the controller, so it counts every attempt, successful or not, and a refused
 * one never reaches the password check. Deliberately not a @Component:
 * Spring Boot would also register a Filter bean as a plain servlet filter,
 * outside the security chain. SecurityConfig instantiates it instead.
 *
 * <p>The 429 body is written by hand in the same shape as shared-kernel's
 * security errors (type/title/status/detail/code), for the same reason: this
 * layer runs before Spring MVC, so GlobalExceptionHandler never sees it.
 */
public class LoginRateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/auth/login";

    private final LoginRateLimiter limiter;
    private final ClientIpResolver clientIpResolver;
    private final ObjectMapper objectMapper;

    public LoginRateLimitFilter(LoginRateLimiter limiter, ClientIpResolver clientIpResolver, ObjectMapper objectMapper) {
        this.limiter = limiter;
        this.clientIpResolver = clientIpResolver;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && LOGIN_PATH.equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        LoginRateLimiter.Decision decision = limiter.tryConsume(clientIpResolver.resolve(request));
        if (decision.allowed()) {
            chain.doFilter(request, response);
            return;
        }
        AuthErrors error = AuthErrors.TOO_MANY_LOGIN_ATTEMPTS;
        response.setStatus(error.getStatus().value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", error.getStatus().getReasonPhrase());
        body.put("status", error.getStatus().value());
        body.put("detail", error.getMessage());
        body.put("code", error.getCode());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

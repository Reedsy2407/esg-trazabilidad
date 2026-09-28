package pe.esgtrazabilidad.auth.ratelimit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    // TEMPORARY (Task 82): logs every forwarding header Render's edge sets, to
    // read which one and which hop carry the caller's real IP. Removed in the
    // same task once ClientIpResolver's rule is confirmed.
    private static final Logger log = LoggerFactory.getLogger(LoginRateLimitFilter.class);
    private static final List<String> FORWARDING_HEADERS = List.of(
            "X-Forwarded-For", "Forwarded", "X-Real-IP", "True-Client-IP", "CF-Connecting-IP", "X-Envoy-External-Address");

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
        logForwardingHeaders(request);
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

    // TEMPORARY (Task 82). CR/LF stripped: header values are caller-controlled.
    private void logForwardingHeaders(HttpServletRequest request) {
        StringBuilder line = new StringBuilder("task82 remoteAddr=").append(request.getRemoteAddr());
        for (String name : FORWARDING_HEADERS) {
            List<String> values = Collections.list(request.getHeaders(name));
            if (!values.isEmpty()) {
                line.append(' ').append(name).append('=').append(values);
            }
        }
        log.info(line.toString().replaceAll("[\r\n]", "_"));
    }
}

package pe.esgtrazabilidad.kernel.observability;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * One line per request, in logfmt with a fixed shape so it can be grepped and summed:
 * {@code event=http_request service=… method=… route=… status=… durationMs=… dbMs=… poolMs=… dbQueries=…}.
 *
 * <p>The route is the controller's path template ({@code /neighbors/{neighborId}}), never the
 * real path, so no identifier reaches the log; a request no controller matched is {@code UNMATCHED}.
 * Nothing else about the request is logged: no query string, headers, body, e-mail or token.
 * INFO for everything that was answered, ERROR when the service failed (5xx or an exception
 * that escaped). Health probes are skipped: Render calls them every few seconds.
 */
public class RequestTimingFilter extends OncePerRequestFilter implements Ordered {

    private static final Logger log = LoggerFactory.getLogger("esg.http");
    private static final String BEST_MATCH = "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

    private final String service;

    public RequestTimingFilter(String service) {
        this.service = service;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        RequestTimings timings = RequestTimings.begin();
        boolean escaped = true;
        try {
            chain.doFilter(request, response);
            escaped = false;
        } finally {
            RequestTimings.end();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            int status = escaped ? 500 : response.getStatus();
            Object pattern = request.getAttribute(BEST_MATCH);
            String line = "event=http_request service=" + service
                    + " method=" + request.getMethod()
                    + " route=" + (pattern == null ? "UNMATCHED" : pattern)
                    + " status=" + status
                    + " durationMs=" + durationMs
                    + " dbMs=" + timings.queryMillis()
                    + " poolMs=" + timings.poolMillis()
                    + " dbQueries=" + timings.queries();
            if (status >= 500) {
                log.error(line);
            } else {
                log.info(line);
            }
        }
    }
}

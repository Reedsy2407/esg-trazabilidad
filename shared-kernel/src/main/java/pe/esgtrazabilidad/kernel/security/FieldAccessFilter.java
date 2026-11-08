package pe.esgtrazabilidad.kernel.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Consent gate for field routes (A.1). For a RECYCLER token on a field route:
 * 409 {@code AUTH-019} without {@code cv}; on a location route (GPS) also
 * without {@code lc=true}. Tokens of other roles and anonymous requests pass
 * untouched, so the role rule ({@code hasRole("RECYCLER")}, 403 AUTH-000) and
 * the 401 keep deciding them.
 *
 * <p>Register it in the service's chain right after the bearer filter so the
 * authentication already exists:
 * {@code http.addFilterAfter(new FieldAccessFilter(mapper, fieldRoutes, locationRoutes),
 * BearerTokenAuthenticationFilter.class)}. Routes whose use of GPS depends on
 * the body call {@link RecyclerClaims#requireLocationConsent()} instead.
 */
public class FieldAccessFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final RequestMatcher fieldRoutes;
    private final RequestMatcher locationRoutes;

    public FieldAccessFilter(ObjectMapper objectMapper, RequestMatcher fieldRoutes, RequestMatcher locationRoutes) {
        this.objectMapper = objectMapper;
        this.fieldRoutes = fieldRoutes;
        this.locationRoutes = locationRoutes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean location = locationRoutes.matches(request);
        if ((location || fieldRoutes.matches(request))
                && SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && isRecycler(token)) {
            RecyclerClaims.ConsentError error = null;
            if (token.getToken().getClaim("cv") == null) {
                error = RecyclerClaims.ConsentError.CONSENT_REQUIRED;
            } else if (location && !Boolean.TRUE.equals(token.getToken().getClaim("lc"))) {
                error = RecyclerClaims.ConsentError.LOCATION_CONSENT_REQUIRED;
            }
            if (error != null) {
                SecurityErrorResponses.write(response, objectMapper, HttpStatus.CONFLICT, error.getCode(), error.getMessage());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isRecycler(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ("ROLE_" + RecyclerClaims.ROLE).equals(authority.getAuthority()));
    }
}

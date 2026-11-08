package pe.esgtrazabilidad.kernel.security;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import pe.esgtrazabilidad.kernel.error.ApplicationError;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * The claims of a RECYCLER access token (A.1): {@code sub} account id,
 * {@code rid} recycler id, {@code aid} association id, {@code did} device id,
 * {@code cv} accepted consent version (absent without a current consent) and
 * {@code lc} (true when location was accepted). Read only from the validated
 * token, never from a request body.
 */
public record RecyclerClaims(
        UUID accountId, UUID recyclerId, UUID associationId, UUID deviceId, Integer consentVersion, boolean locationConsent) {

    public static final String ROLE = "RECYCLER";

    /** AUTH-019, 409: consent (general, or the location one) is required. */
    public enum ConsentError implements ApplicationError {
        CONSENT_REQUIRED("Consentimiento requerido"),
        LOCATION_CONSENT_REQUIRED("Consentimiento de ubicación requerido");

        private final String message;

        ConsentError(String message) {
            this.message = message;
        }

        @Override
        public String getCode() {
            return "AUTH-019";
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatus getStatus() {
            return HttpStatus.CONFLICT;
        }
    }

    public static RecyclerClaims from(Jwt jwt) {
        if (jwt == null || !ROLE.equals(jwt.getClaimAsString("role"))) {
            throw new IllegalStateException("No authenticated recycler in this request");
        }
        return new RecyclerClaims(
                uuid(jwt, "sub"),
                uuid(jwt, "rid"),
                uuid(jwt, "aid"),
                uuid(jwt, "did"),
                consentVersion(jwt),
                Boolean.TRUE.equals(jwt.getClaim("lc")));
    }

    public boolean hasConsent() {
        return consentVersion != null;
    }

    /** For routes that need consent: throws AUTH-019 (409) when {@code cv} is missing. */
    public void requireConsent() {
        if (!hasConsent()) {
            throw new ApplicationException(ConsentError.CONSENT_REQUIRED);
        }
    }

    /** For routes (or body-dependent cases) that use GPS: needs consent and {@code lc=true}. */
    public void requireLocationConsent() {
        requireConsent();
        if (!locationConsent) {
            throw new ApplicationException(ConsentError.LOCATION_CONSENT_REQUIRED);
        }
    }

    private static UUID uuid(Jwt jwt, String claim) {
        String value = jwt.getClaimAsString(claim);
        if (value == null) {
            throw new IllegalStateException("Recycler token without claim " + claim);
        }
        return UUID.fromString(value);
    }

    private static Integer consentVersion(Jwt jwt) {
        Object value = jwt.getClaim("cv");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}

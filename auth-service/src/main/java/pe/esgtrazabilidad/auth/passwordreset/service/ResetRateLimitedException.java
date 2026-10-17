package pe.esgtrazabilidad.auth.passwordreset.service;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/** AUTH-012 plus when to try again, for the Retry-After header. */
public class ResetRateLimitedException extends ApplicationException {

    private final long retryAfterSeconds;

    public ResetRateLimitedException(long retryAfterSeconds) {
        super(AuthErrors.TOO_MANY_RESET_ATTEMPTS);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

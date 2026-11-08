package pe.esgtrazabilidad.auth.recycleraccount.service;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/** AUTH-015 plus when to try again, for the Retry-After header. */
public class ActivationRateLimitedException extends ApplicationException {

    private final long retryAfterSeconds;

    public ActivationRateLimitedException(long retryAfterSeconds) {
        super(AuthErrors.TOO_MANY_ACTIVATION_ATTEMPTS);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}

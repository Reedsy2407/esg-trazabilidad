package pe.esgtrazabilidad.auth.recycleraccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * The only place the activation code exists in clear: the answer to the call that issued it.
 * {@code toString} hides it so it can never reach a log by accident.
 */
public record IssuedCode(UUID accountId, String activationCode, Instant expiresAt) {

    @Override
    public String toString() {
        return "IssuedCode[accountId=" + accountId + ", expiresAt=" + expiresAt + "]";
    }
}

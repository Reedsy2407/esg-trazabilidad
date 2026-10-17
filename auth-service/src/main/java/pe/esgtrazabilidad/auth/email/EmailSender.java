package pe.esgtrazabilidad.auth.email;

import java.time.Instant;

/**
 * Port for the two emails auth-service sends. It states intentions, not providers: each
 * adapter owns its template. A failure is an {@link EmailSendException} with a fixed reason
 * code; an implementation never retries (it has no idempotency key, so a retry could send the
 * email twice) and never logs the recipient, the code, the secret or the provider's answer.
 */
public interface EmailSender {

    /** The 8-digit code the person types to choose a new password. */
    void sendResetCode(String to, String code);

    /** A notice that the account's password changed at {@code when}. It carries no password or code. */
    void sendPasswordChanged(String to, Instant when);
}

package pe.esgtrazabilidad.auth.passwordreset.port.in;

public interface PasswordResetUseCase {

    /**
     * Sends a code to the account's email when there is an active account and no limit applies. It
     * answers the same way, and does the same work, whether or not an email was sent; only a limit
     * about the caller's own address is reported (AUTH-012).
     */
    void request(String email, String clientIp);

    /**
     * Spends the code and sets the new password in one step. AUTH-011 for any code that does not work
     * (wrong, expired, spent, never issued, unknown or inactive account), AUTH-007 for a password the
     * policy refuses or that repeats the current one, AUTH-012 when the caller's address is over its limit.
     */
    void confirm(String email, String code, String newRawPassword, String clientIp);
}

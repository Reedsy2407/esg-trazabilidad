package pe.esgtrazabilidad.auth.passwordreset.port.in;

public interface PasswordResetUseCase {

    /**
     * Sends a code to the account's email when there is an active account and no limit applies. It
     * answers the same way, and does the same work, whether or not an email was sent; only a limit
     * about the caller's own address is reported (AUTH-012).
     */
    void request(String email, String clientIp);

    /**
     * Step 1 of the recovery: checks the code and, if it is right, spends it and returns a reset token
     * (valid 10 minutes, one use). AUTH-011 for any code that does not work (wrong, expired, spent, never
     * issued, unknown or inactive account, malformed), with the same work in every case; AUTH-012 when the
     * caller's address is over its limit.
     */
    String verify(String email, String code, String clientIp);

    /**
     * Step 2: spends the token and sets the new password in one step. AUTH-011 for a token that does not
     * work (unknown, expired, spent, malformed), AUTH-007 for a password the policy refuses or that repeats
     * the current one (the token is NOT spent, so the person can try another), AUTH-012 when the caller's
     * address is over its limit.
     */
    void confirm(String resetToken, String newRawPassword, String clientIp);
}

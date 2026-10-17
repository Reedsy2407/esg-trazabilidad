package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

/** The proof that the code was right: sent back with the new password within 10 minutes, once. */
public record VerifyPasswordResetResponse(String resetToken) {
}

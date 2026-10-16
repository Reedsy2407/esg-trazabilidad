package pe.esgtrazabilidad.auth.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

/**
 * ONE shared enum for all of auth-service's entities (AUTH-xxx) -- same
 * one-enum-per-service convention collection-service/reporting-service use.
 */
public enum AuthErrors implements ApplicationError {

    // Deliberately the same error for "no such email" and "wrong password"
    // -- a classic enumeration-prevention rule, never let a caller
    // distinguish "that account doesn't exist" from "that password is wrong".
    INVALID_CREDENTIALS("AUTH-001", "Credenciales inválidas", HttpStatus.UNAUTHORIZED),
    DUPLICATE_STAFF_EMAIL("AUTH-002", "Ya existe una cuenta de staff con ese email", HttpStatus.CONFLICT),
    // Defensive: the token's sub always refers to a real account at issuance
    // time, and there's no delete/deactivation endpoint in this MVP, so this
    // path is expected to be effectively unreachable in practice -- kept for
    // the same reason every other *NotFoundUseCase in this codebase has one.
    STAFF_USER_NOT_FOUND("AUTH-003", "Cuenta de staff no encontrada", HttpStatus.NOT_FOUND),
    // POST /auth/login's per-client-IP rate limit (SPEC-deployment.md).
    TOO_MANY_LOGIN_ATTEMPTS(
            "AUTH-004", "Demasiados intentos de inicio de sesión, intenta más tarde", HttpStatus.TOO_MANY_REQUESTS),
    // POST /auth/staff-users: an email @Email accepts but StaffUser.isValidEmail
    // doesn't (no dot in the domain, e.g. "ana@empresa").
    INVALID_STAFF_EMAIL("AUTH-005", "El email no es válido", HttpStatus.BAD_REQUEST),
    // Decided from the database, not from the token's role claim.
    ADMIN_REQUIRED("AUTH-006", "Solo un administrador puede hacer esto", HttpStatus.FORBIDDEN),
    // NIST SP 800-63B: 15 to 64 characters, not a common password, not built from the email or the product name.
    WEAK_PASSWORD(
            "AUTH-007",
            "La contraseña debe tener entre 15 y 64 caracteres y no puede ser una contraseña común ni incluir tu correo",
            HttpStatus.BAD_REQUEST),
    // 400, not 401: the frontend ends the session on any 401, and this is a form error.
    WRONG_CURRENT_PASSWORD("AUTH-008", "La contraseña actual no es correcta", HttpStatus.BAD_REQUEST),
    // Deactivating or resetting your own account from the admin screens.
    CANNOT_ACT_ON_OWN_ACCOUNT("AUTH-009", "No puedes hacer esto con tu propia cuenta", HttpStatus.CONFLICT),
    STATUS_UNCHANGED("AUTH-010", "La cuenta ya está en ese estado", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AuthErrors(String code, String message, HttpStatus status) {
        this.code = code;
        this.message = message;
        this.status = status;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public HttpStatus getStatus() {
        return status;
    }
}

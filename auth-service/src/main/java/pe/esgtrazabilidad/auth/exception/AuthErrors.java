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
    STATUS_UNCHANGED("AUTH-010", "La cuenta ya está en ese estado", HttpStatus.CONFLICT),
    // Password recovery (POST /auth/password-reset/*). The same answer for a wrong, expired, spent or
    // never-issued code and for an unknown or inactive account: the caller learns nothing about accounts.
    INVALID_RESET_CODE("AUTH-011", "El código no es válido o venció. Pide uno nuevo.", HttpStatus.BAD_REQUEST),
    // A limit about the caller (their address), never about an account. Carries Retry-After.
    TOO_MANY_RESET_ATTEMPTS("AUTH-012", "Demasiados intentos, prueba de nuevo más tarde", HttpStatus.TOO_MANY_REQUESTS),
    // AUTH-013 is retired and never reused. AUTH-019 (consent) is emitted by shared-kernel's RecyclerClaims.
    // One answer for a wrong, expired, replaced, used or malformed code: the caller learns nothing about which.
    INVALID_ACTIVATION_CODE("AUTH-014", "El código no es válido o venció. Pide uno nuevo al personal.", HttpStatus.BAD_REQUEST),
    // Refresh token unknown, or the device went 30 days unused or past its 90 day limit. 401: the app asks for a new code.
    REFRESH_INVALID("AUTH-016", "La sesión no es válida. Pide un código nuevo al personal.", HttpStatus.UNAUTHORIZED),
    // The device was revoked (by staff, by logout, or because an old token was reused outside its window).
    DEVICE_REVOKED("AUTH-017", "Este dispositivo fue revocado. Pide un código nuevo al personal.", HttpStatus.UNAUTHORIZED),
    // A limit on the caller's address or on the typed code. Carries Retry-After.
    TOO_MANY_ACTIVATION_ATTEMPTS("AUTH-015", "Demasiados intentos, prueba de nuevo más tarde", HttpStatus.TOO_MANY_REQUESTS),
    // The account or the recycler behind it is not active (recycler-service turned it off).
    RECYCLER_INACTIVE("AUTH-018", "La cuenta o el reciclador no está activo", HttpStatus.FORBIDDEN),
    // The status copy has no row yet: the recycler's event has not arrived. Retryable, never a made-up state.
    RECYCLER_NOT_SYNCED(
            "AUTH-020",
            "El reciclador aún no está sincronizado en el sistema de acceso. Inténtalo de nuevo en unos minutos",
            HttpStatus.CONFLICT);

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

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
    DUPLICATE_STAFF_EMAIL("AUTH-002", "Ya existe una cuenta de staff con ese email", HttpStatus.CONFLICT);

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

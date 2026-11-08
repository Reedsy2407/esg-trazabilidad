package pe.esgtrazabilidad.auth.recycleraccount.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

/**
 * Two answers the specification (Parte A) gives a status but no AUTH code for. They use the generic
 * codes the shared handler already emits ({@code DATA_CONFLICT}, {@code NOT_FOUND}) rather than a new
 * AUTH number, which the specification does not allow.
 */
public enum RecyclerAccountErrors implements ApplicationError {

    /** Creating a code for a recycler whose account is already active, or reissuing while a device lives. */
    ACCOUNT_ALREADY_ACTIVE("DATA_CONFLICT", "La cuenta del reciclador ya está activa", HttpStatus.CONFLICT),
    ACCOUNT_NOT_FOUND("NOT_FOUND", "Cuenta de reciclador no encontrada", HttpStatus.NOT_FOUND),
    DEVICE_NOT_FOUND("NOT_FOUND", "Dispositivo no encontrado", HttpStatus.NOT_FOUND),
    /** The person answered a consent version that is no longer the one in force (a period changed meanwhile). */
    CONSENT_VERSION_OUTDATED(
            "VALIDATION_ERROR", "La versión del consentimiento ya no es la vigente. Vuelve a leerlo.", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    RecyclerAccountErrors(String code, String message, HttpStatus status) {
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

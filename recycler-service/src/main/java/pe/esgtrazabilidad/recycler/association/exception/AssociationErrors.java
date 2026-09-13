package pe.esgtrazabilidad.recycler.association.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

public enum AssociationErrors implements ApplicationError {

    NOT_FOUND("ASO-001", "Asociación no encontrada", HttpStatus.NOT_FOUND),
    DUPLICATE_RUC("ASO-002", "Ya existe una asociación con ese RUC", HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(
            "ASO-003", "La asociación ya se encuentra en el estado solicitado", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    AssociationErrors(String code, String message, HttpStatus status) {
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

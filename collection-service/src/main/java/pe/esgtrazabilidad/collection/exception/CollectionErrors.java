package pe.esgtrazabilidad.collection.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

public enum CollectionErrors implements ApplicationError {

    NEIGHBOR_NOT_FOUND("COL-001", "Vecino no encontrado", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CollectionErrors(String code, String message, HttpStatus status) {
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

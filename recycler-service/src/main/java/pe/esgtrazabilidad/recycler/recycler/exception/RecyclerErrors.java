package pe.esgtrazabilidad.recycler.recycler.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

public enum RecyclerErrors implements ApplicationError {

    NOT_FOUND("REC-001", "Reciclador no encontrado", HttpStatus.NOT_FOUND),
    DUPLICATE_DNI("REC-002", "Ya existe un reciclador con ese DNI", HttpStatus.CONFLICT),
    ASSOCIATION_NOT_FOUND("REC-003", "La asociación indicada no existe", HttpStatus.NOT_FOUND);

    private final String code;
    private final String message;
    private final HttpStatus status;

    RecyclerErrors(String code, String message, HttpStatus status) {
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

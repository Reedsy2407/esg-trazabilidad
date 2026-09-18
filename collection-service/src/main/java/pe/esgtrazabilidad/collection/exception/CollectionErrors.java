package pe.esgtrazabilidad.collection.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

public enum CollectionErrors implements ApplicationError {

    NEIGHBOR_NOT_FOUND("COL-001", "Vecino no encontrado", HttpStatus.NOT_FOUND),
    SCHEDULE_CONFLICT("COL-002", "Conflicto de horario de recojo", HttpStatus.CONFLICT),
    COMPANY_NOT_FOUND("COL-004", "Empresa no encontrada", HttpStatus.NOT_FOUND),
    DUPLICATE_RUC("COL-005", "Ya existe una empresa con ese RUC", HttpStatus.CONFLICT),
    SCHEDULE_NOT_FOUND("COL-006", "Programación de recojo no encontrada", HttpStatus.NOT_FOUND),
    RECORD_NOT_FOUND("COL-007", "Registro de recojo no encontrado", HttpStatus.NOT_FOUND),
    INVALID_SCHEDULE_TRANSITION(
            "COL-008", "Transición de estado no permitida para esta programación", HttpStatus.CONFLICT),
    ASSOCIATION_BLOCKED(
            "COL-009",
            "La asociación tiene una certificación vencida y no puede registrar recojos",
            HttpStatus.CONFLICT);

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

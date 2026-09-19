package pe.esgtrazabilidad.reporting.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

/**
 * ONE shared enum for all of reporting-service's entities (RPT-xxx) --
 * same one-enum-per-service convention collection-service uses, not one
 * per entity like recycler-service.
 */
public enum ReportingErrors implements ApplicationError {

    TRACKED_COMPANY_NOT_FOUND("RPT-001", "Empresa no encontrada en reporting-service", HttpStatus.NOT_FOUND),
    DUPLICATE_TRACKED_COMPANY_RUC("RPT-002", "Ya existe una empresa rastreada con ese RUC", HttpStatus.CONFLICT);

    private final String code;
    private final String message;
    private final HttpStatus status;

    ReportingErrors(String code, String message, HttpStatus status) {
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

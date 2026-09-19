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
    DUPLICATE_TRACKED_COMPANY_RUC("RPT-002", "Ya existe una empresa rastreada con ese RUC", HttpStatus.CONFLICT),
    CERTIFICATE_NOT_FOUND("RPT-003", "Certificado no encontrado", HttpStatus.NOT_FOUND),
    OVERLAPPING_CERTIFICATE_PERIOD(
            "RPT-004",
            "Ya existe un certificado emitido que se superpone con ese periodo",
            HttpStatus.CONFLICT),
    MISSING_SIGERSOL_DATA_FOR_PERIOD(
            "RPT-005",
            "No hay datos oficiales de SIGERSOL cargados para esa asociación y periodo",
            HttpStatus.CONFLICT),
    DUPLICATE_SIGERSOL_SYNC_PERIOD(
            "RPT-006",
            "Ya existe un registro SIGERSOL para esa asociación que se superpone con ese periodo",
            HttpStatus.CONFLICT),
    SIGERSOL_SYNC_NOT_FOUND("RPT-007", "Registro SIGERSOL no encontrado", HttpStatus.NOT_FOUND),
    // Message deliberately generic, not "periodo inválido": SigersolSync.create()
    // throws IllegalArgumentException for more than one reason (invalid period
    // range, negative officialKilosDeclared) and SigersolSyncService.register()
    // catches all of them into this one error, same as CertificationService's
    // own single-reason catch for CER-003 -- but this domain has more than one
    // reason, so the message can't name just "periodo" without being wrong for
    // the other cause.
    INVALID_SIGERSOL_SYNC_DATA("RPT-008", "Los datos del registro SIGERSOL no son válidos", HttpStatus.BAD_REQUEST);

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

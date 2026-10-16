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
            "No hay un cumplimiento de jerarquía declarado para esa asociación que cubra todo el período",
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
    INVALID_SIGERSOL_SYNC_DATA("RPT-008", "Los datos del registro SIGERSOL no son válidos", HttpStatus.BAD_REQUEST),
    // Certificate issue and summary preview: the end date is before the start.
    // Checked by CertificateService before any lookup, so it never reaches
    // EsgCertificate.issue()'s own IllegalArgumentException (which was a 500).
    INVALID_CERTIFICATE_PERIOD(
            "RPT-009", "El periodo no es válido: la fecha final es anterior a la inicial", HttpStatus.BAD_REQUEST),
    INVALID_RUC("RPT-010", "El RUC no es válido", HttpStatus.BAD_REQUEST),
    SUNAT_UNAVAILABLE(
            "RPT-011",
            "No se pudo consultar SUNAT. Puede registrar la empresa como no verificada",
            HttpStatus.SERVICE_UNAVAILABLE),
    RUC_NOT_FOUND_IN_SUNAT("RPT-012", "SUNAT no tiene registrado ese RUC", HttpStatus.UNPROCESSABLE_ENTITY),
    RUC_DEREGISTERED_IN_SUNAT(
            "RPT-013", "El RUC está en baja definitiva en SUNAT", HttpStatus.UNPROCESSABLE_ENTITY),
    SUNAT_ANSWERED(
            "RPT-014", "SUNAT respondió: registre la empresa con la consulta normal", HttpStatus.CONFLICT),
    INACTIVE_TRACKED_COMPANY(
            "RPT-015", "La empresa está inactiva: no se pueden emitir certificados", HttpStatus.CONFLICT),
    UNVERIFIED_TRACKED_COMPANY(
            "RPT-016",
            "La empresa no está verificada con SUNAT: verifíquela antes de emitir",
            HttpStatus.CONFLICT),
    TRACKED_COMPANY_HAS_CERTIFICATES(
            "RPT-017",
            "La empresa tiene certificados: no se puede eliminar ni cambiar su RUC o asociación",
            HttpStatus.CONFLICT),
    TRACKED_COMPANY_ALREADY_IN_STATUS(
            "RPT-018", "La empresa ya se encuentra en el estado solicitado", HttpStatus.CONFLICT),
    CERTIFICATE_ALREADY_ANNULLED("RPT-019", "El certificado ya está anulado", HttpStatus.CONFLICT),
    // A SIGERSOL record whose origin is "con comprobante referenciado" must say which proof it points to.
    MISSING_SIGERSOL_REFERENCE(
            "RPT-021",
            "Indica la referencia del comprobante: es obligatoria cuando el origen es «con comprobante referenciado»",
            HttpStatus.BAD_REQUEST);

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

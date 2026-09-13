package pe.esgtrazabilidad.recycler.certification.exception;

import org.springframework.http.HttpStatus;

import pe.esgtrazabilidad.kernel.error.ApplicationError;

public enum CertificationErrors implements ApplicationError {

    NOT_FOUND("CER-001", "Certificación no encontrada", HttpStatus.NOT_FOUND),
    ASSOCIATION_NOT_FOUND("CER-002", "La asociación indicada no existe", HttpStatus.NOT_FOUND),
    INVALID_DATE_RANGE(
            "CER-003", "La fecha de emisión debe ser anterior a la fecha de vencimiento", HttpStatus.BAD_REQUEST);

    private final String code;
    private final String message;
    private final HttpStatus status;

    CertificationErrors(String code, String message, HttpStatus status) {
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

package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.reporting.exception.ReportingErrors;

/**
 * Catches the DB-level fallback for RPT-004: CertificateService's
 * existsOverlapping()-then-save() check isn't atomic, so two concurrent
 * requests can both pass it before either commits, leaving Postgres's
 * EXCLUDE USING gist constraint on esg_certificate (Task 50) as the real
 * guard (to be proven with a real two-thread test in Task 53). esg_certificate
 * has exactly one constraint of this kind, so no getConstraintName()
 * disambiguation is needed -- same simpler pattern as
 * SigersolSyncExceptionHandler/TrackedCompanyExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = CertificateController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CertificateExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleOverlappingPeriod(DataIntegrityViolationException exception) {
        ReportingErrors error = ReportingErrors.OVERLAPPING_CERTIFICATE_PERIOD;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

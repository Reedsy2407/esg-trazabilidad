package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.reporting.exception.ReportingErrors;

/**
 * The DB-level fallback of the RUC uniqueness rule: the findByRuc()-then-write
 * check isn't atomic, so two concurrent requests with the same RUC can both
 * pass it and the partial unique index {@code uq_tracked_company_ruc_active}
 * is the real guard. Only that index means RPT-002: any other integrity
 * violation (a CHECK, a foreign key) is a different problem and answers a
 * generic conflict instead of claiming the RUC is taken.
 */
@RestControllerAdvice(assignableTypes = TrackedCompanyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrackedCompanyExceptionHandler {

    static final String RUC_INDEX = "uq_tracked_company_ruc_active";

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleIntegrityViolation(DataIntegrityViolationException exception) {
        if (violatesRucIndex(exception)) {
            ReportingErrors error = ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC;
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
            problemDetail.setProperty("code", error.getCode());
            return problemDetail;
        }
        ProblemDetail problemDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "La operación choca con datos ya registrados");
        problemDetail.setProperty("code", "DATA_CONFLICT");
        return problemDetail;
    }

    private static boolean violatesRucIndex(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation
                    && RUC_INDEX.equals(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}

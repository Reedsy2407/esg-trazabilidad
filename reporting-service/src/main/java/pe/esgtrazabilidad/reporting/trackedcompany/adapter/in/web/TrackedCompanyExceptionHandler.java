package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.reporting.exception.ReportingErrors;

/**
 * Catches the DB-level fallback of the RUC uniqueness rule:
 * TrackedCompanyService's findByRuc()-then-save() check isn't atomic, so two
 * concurrent requests with the same RUC can both pass it before either
 * saves, leaving Postgres's UNIQUE constraint as the real guard.
 * TrackedCompany has exactly one unique constraint (ruc), so no
 * ConstraintViolationException.getConstraintName() disambiguation is needed
 * here -- same simpler pattern as CompanyExceptionHandler in
 * collection-service.
 */
@RestControllerAdvice(assignableTypes = TrackedCompanyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class TrackedCompanyExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDuplicateRuc(DataIntegrityViolationException exception) {
        ReportingErrors error = ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

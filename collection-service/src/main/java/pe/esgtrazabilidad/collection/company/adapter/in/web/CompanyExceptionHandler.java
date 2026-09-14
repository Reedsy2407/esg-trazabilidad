package pe.esgtrazabilidad.collection.company.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;

/**
 * Catches the DB-level fallback of the RUC uniqueness rule: CompanyService's
 * findByRuc()-then-save() check isn't atomic, so two concurrent requests with the
 * same RUC can both pass it before either saves, leaving Postgres's UNIQUE
 * constraint as the real guard. Company has exactly one unique constraint (ruc),
 * so no ConstraintViolationException.getConstraintName() disambiguation is needed
 * here -- same simpler pattern as AssociationExceptionHandler in recycler-service,
 * not the multi-constraint pattern RecyclerExceptionHandler needs.
 */
@RestControllerAdvice(assignableTypes = CompanyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CompanyExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDuplicateRuc(DataIntegrityViolationException exception) {
        CollectionErrors error = CollectionErrors.DUPLICATE_RUC;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

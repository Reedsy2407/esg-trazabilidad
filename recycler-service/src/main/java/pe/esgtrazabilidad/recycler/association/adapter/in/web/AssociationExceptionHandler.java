package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.recycler.association.exception.AssociationErrors;

/**
 * Catches the DB-level fallback of the RUC uniqueness rule: AssociationService's
 * findByRuc()-then-save() check isn't atomic, so two concurrent requests with the
 * same RUC can both pass it before either saves, leaving Postgres's UNIQUE
 * constraint as the real guard. Scoped to AssociationController only, since
 * "a DataIntegrityViolationException here means duplicate RUC" is association-
 * specific knowledge that doesn't belong in shared-kernel's GlobalExceptionHandler.
 * Ordered ahead of GlobalExceptionHandler's generic DATA_CONFLICT fallback, which
 * also matches DataIntegrityViolationException — this one is more specific.
 */
@RestControllerAdvice(assignableTypes = AssociationController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class AssociationExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDuplicateRuc(DataIntegrityViolationException exception) {
        AssociationErrors error = AssociationErrors.DUPLICATE_RUC;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.auth.exception.AuthErrors;

/**
 * Catches the DB-level fallback of the email uniqueness rule:
 * StaffUserService's findByEmail()-then-save() check isn't atomic, so two
 * concurrent requests with the same email can both pass it before either
 * saves, leaving Postgres's UNIQUE constraint as the real guard.
 * StaffUser has exactly one unique constraint (email), so no
 * getConstraintName() disambiguation is needed -- same simpler pattern as
 * TrackedCompanyExceptionHandler (reporting-service).
 */
@RestControllerAdvice(assignableTypes = StaffUserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class StaffUserExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDuplicateEmail(DataIntegrityViolationException exception) {
        AuthErrors error = AuthErrors.DUPLICATE_STAFF_EMAIL;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

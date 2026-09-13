package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;

/**
 * Catches the DB-level fallback for Recycler's two race-prone invariants:
 * RecyclerService's findByDni()-then-save() and its association-exists check
 * are both TOCTOU-racy the same way AssociationService's RUC check is. Unlike
 * Association, Recycler has TWO constraints that surface as the same
 * DataIntegrityViolationException type (unique dni, FK to association), so
 * the constraint name — not the exception type — decides which typed error
 * applies. getConstraintName() (via the Hibernate-specific cause) is used
 * instead of parsing the exception message, which varies by driver/locale.
 */
@RestControllerAdvice(assignableTypes = RecyclerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecyclerExceptionHandler {

    private static final String DNI_UNIQUE_CONSTRAINT = "recycler_dni_key";
    private static final String ASSOCIATION_FK_CONSTRAINT = "fk_recycler_association";

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String constraintName = constraintNameOf(exception);

        if (ASSOCIATION_FK_CONSTRAINT.equals(constraintName)) {
            return problemDetailFor(RecyclerErrors.ASSOCIATION_NOT_FOUND);
        }
        if (DNI_UNIQUE_CONSTRAINT.equals(constraintName)) {
            return problemDetailFor(RecyclerErrors.DUPLICATE_DNI);
        }

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La operación viola una restricción de integridad de datos");
        problemDetail.setProperty("code", "DATA_CONFLICT");
        return problemDetail;
    }

    private static String constraintNameOf(DataIntegrityViolationException exception) {
        return exception.getCause() instanceof ConstraintViolationException constraintViolation
                ? constraintViolation.getConstraintName()
                : null;
    }

    private ProblemDetail problemDetailFor(RecyclerErrors error) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

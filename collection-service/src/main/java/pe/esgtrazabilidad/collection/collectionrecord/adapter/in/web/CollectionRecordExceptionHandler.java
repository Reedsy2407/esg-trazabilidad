package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import org.hibernate.exception.ConstraintViolationException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;

/**
 * Catches the DB-level fallback for CollectionRecord's neighbor-exists check:
 * CollectionRecordService's findById()-then-save() isn't atomic, the same
 * TOCTOU shape as every other FK check in this codebase. Two FK constraints
 * on this table (neighbor, schedule), so the constraint name decides which
 * applies -- same getConstraintName() pattern as ScheduleExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = CollectionRecordController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CollectionRecordExceptionHandler {

    private static final String NEIGHBOR_FK_CONSTRAINT = "fk_collection_record_neighbor";
    private static final String SCHEDULE_FK_CONSTRAINT = "fk_collection_record_schedule";

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String constraintName = constraintNameOf(exception);

        if (NEIGHBOR_FK_CONSTRAINT.equals(constraintName)) {
            return problemDetailFor(CollectionErrors.NEIGHBOR_NOT_FOUND);
        }
        if (SCHEDULE_FK_CONSTRAINT.equals(constraintName)) {
            return problemDetailFor(CollectionErrors.SCHEDULE_NOT_FOUND);
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

    private ProblemDetail problemDetailFor(CollectionErrors error) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

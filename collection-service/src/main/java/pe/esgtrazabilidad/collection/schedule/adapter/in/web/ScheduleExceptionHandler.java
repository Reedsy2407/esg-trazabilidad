package pe.esgtrazabilidad.collection.schedule.adapter.in.web;

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
 * Catches the DB-level fallback for CollectionSchedule's two race-prone
 * invariants: CollectionScheduleService's assertNoActiveConflict()-then-save()
 * check isn't atomic, and the neighbor-exists check has the same TOCTOU shape.
 * Unlike Neighbor/Company (one constraint each), this table has TWO --
 * the partial unique index (COL-002) and the FK to neighbor (COL-001) -- so
 * the constraint name, not just the exception type, decides which applies.
 * Same getConstraintName() pattern as recycler-service's RecyclerExceptionHandler.
 */
@RestControllerAdvice(assignableTypes = CollectionScheduleController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ScheduleExceptionHandler {

    private static final String ACTIVE_CONFLICT_INDEX = "ux_collection_schedule_neighbor_day_active";
    private static final String NEIGHBOR_FK_CONSTRAINT = "fk_collection_schedule_neighbor";

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        String constraintName = constraintNameOf(exception);

        if (ACTIVE_CONFLICT_INDEX.equals(constraintName)) {
            return problemDetailFor(CollectionErrors.SCHEDULE_CONFLICT);
        }
        if (NEIGHBOR_FK_CONSTRAINT.equals(constraintName)) {
            return problemDetailFor(CollectionErrors.NEIGHBOR_NOT_FOUND);
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

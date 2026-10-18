package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import java.sql.SQLException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.reporting.exception.ReportingErrors;

/**
 * Catches the DB-level fallback for RPT-006: SigersolSyncService's
 * existsOverlapping()-then-save() check isn't atomic, so two concurrent
 * requests can both pass it before either commits, leaving Postgres's
 * EXCLUDE USING gist constraint as the real guard (see Task 44 -- proven
 * with a real two-thread test in Task 46). sigersol_sync has exactly one
 * constraint of this kind, so no getConstraintName() disambiguation is
 * needed -- same simpler pattern as TrackedCompanyExceptionHandler.
 *
 * The constraint has two ways of saying no. When one insert has already
 * committed, the other fails with an exclusion violation (23P01, a
 * DataIntegrityViolationException). When both are in flight at the same
 * instant, each waits for the other's row and Postgres aborts one of them as
 * the deadlock victim (40P01), or as a serialization failure (40001): a
 * PessimisticLockingFailureException. Both are the same conflict (the period
 * overlaps another registration that wins), so both answer 409 RPT-006. No retry:
 * the other request has taken the period, so retrying would only fail again.
 */
@RestControllerAdvice(assignableTypes = SigersolSyncController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class SigersolSyncExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleOverlappingPeriod(DataIntegrityViolationException exception) {
        return overlappingPeriod();
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ProblemDetail handleLostTheRace(PessimisticLockingFailureException exception) {
        if (isDeadlockOrSerializationFailure(exception)) {
            return overlappingPeriod();
        }
        throw exception; // a lock timeout or anything else is not this conflict: it stays a 500
    }

    /** SQLState 40P01 (deadlock detected) or 40001 (serialization failure) anywhere in the cause chain. */
    static boolean isDeadlockOrSerializationFailure(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && ("40P01".equals(sql.getSQLState()) || "40001".equals(sql.getSQLState()))) {
                return true;
            }
        }
        return false;
    }

    private static ProblemDetail overlappingPeriod() {
        ReportingErrors error = ReportingErrors.DUPLICATE_SIGERSOL_SYNC_PERIOD;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
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
 */
@RestControllerAdvice(assignableTypes = SigersolSyncController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class SigersolSyncExceptionHandler {

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleOverlappingPeriod(DataIntegrityViolationException exception) {
        ReportingErrors error = ReportingErrors.DUPLICATE_SIGERSOL_SYNC_PERIOD;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }
}

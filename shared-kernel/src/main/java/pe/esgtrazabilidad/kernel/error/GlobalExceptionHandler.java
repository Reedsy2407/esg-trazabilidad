package pe.esgtrazabilidad.kernel.error;

import java.sql.SQLException;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Applies everywhere as the last-resort fallback. A controller-scoped advice
 * (assignableTypes) with a lower @Order value always wins for the same
 * exception type when it has something more specific to say — e.g. mapping
 * DataIntegrityViolationException to a precise domain error code instead of
 * the generic one here.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    /** About how long a free-plan database takes to wake; a hint, not a promise. */
    static final int RETRY_AFTER_SECONDS = 10;

    @ExceptionHandler(ApplicationException.class)
    public ProblemDetail handleApplicationException(ApplicationException exception) {
        ApplicationError error = exception.getError();
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return problemDetail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationException(MethodArgumentNotValidException exception) {
        String detail = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> "%s: %s".formatted(fieldError.getField(), fieldError.getDefaultMessage()))
                .collect(Collectors.joining("; "));
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        return problemDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "La operación viola una restricción de integridad de datos");
        problemDetail.setProperty("code", "DATA_CONFLICT");
        return problemDetail;
    }

    /**
     * The database cannot be reached or did not answer in time (it is waking, restarting, out of
     * connections or down). Without this handler the caller gets a raw 500 whose body says nothing it
     * can act on. A 503 with a stable code and Retry-After tells a client the request can be repeated
     * later; nothing about the cause (host, SQL, driver message) leaves the service, and the log line
     * carries only the exception's class.
     */
    @ExceptionHandler({
        DataAccessResourceFailureException.class,
        TransientDataAccessResourceException.class,
        QueryTimeoutException.class,
        CannotCreateTransactionException.class
    })
    public ResponseEntity<ProblemDetail> handleUnavailable(Exception exception) {
        return unavailable(exception);
    }

    /**
     * A request that takes a pooled connection the database already closed (it went down a moment ago; the
     * pool only checks a connection that has been idle for a while) fails inside the query, not when
     * connecting, so the driver's error arrives wrapped as a generic, uncategorised data-access exception
     * (JpaSystemException, UncategorizedSQLException) that the handler above does not see. When the cause
     * chain shows the connection was lost (SQLState class 08, or 57P01/57P02/57P03: the server is shutting
     * down or crashed) it is the same outage and answers the same 503. Any other uncategorised failure is a
     * real fault: it is rethrown and stays a 500.
     */
    @ExceptionHandler(UncategorizedDataAccessException.class)
    public ResponseEntity<ProblemDetail> handleConnectionLost(UncategorizedDataAccessException exception)
            throws UncategorizedDataAccessException {
        for (Throwable cause = exception; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
            if (cause instanceof SQLException sql && isConnectionLost(sql.getSQLState())) {
                return unavailable(exception);
            }
        }
        throw exception;
    }

    static boolean isConnectionLost(String sqlState) {
        return sqlState != null
                && (sqlState.startsWith("08") || sqlState.equals("57P01") || sqlState.equals("57P02") || sqlState.equals("57P03"));
    }

    private ResponseEntity<ProblemDetail> unavailable(Exception exception) {
        log.warn("event=service_unavailable cause={}", exception.getClass().getSimpleName());
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, "El servicio no está disponible en este momento. Inténtalo de nuevo en un momento.");
        problemDetail.setProperty("code", "SERVICE_UNAVAILABLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(RETRY_AFTER_SECONDS))
                .body(problemDetail);
    }

    /**
     * A path variable or query param that can't be converted to its target
     * type (e.g. a malformed UUID or date) never reaches @Valid -- Spring
     * rejects it during argument resolution, before the controller method
     * runs. Without this handler it falls through to a raw 500.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        String requiredType =
                exception.getRequiredType() != null ? exception.getRequiredType().getSimpleName() : "desconocido";
        String detail = "%s: valor inválido '%s' para el tipo %s"
                .formatted(exception.getName(), exception.getValue(), requiredType);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        return problemDetail;
    }

    /**
     * A request body that isn't valid JSON, or whose JSON doesn't match the
     * DTO's types (e.g. a malformed UUID string in a field), fails during
     * deserialization -- before @Valid runs, since there's no object to
     * validate yet. Without this handler it falls through to a raw 500.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es válido o está mal formado");
        problemDetail.setProperty("code", "VALIDATION_ERROR");
        return problemDetail;
    }
}

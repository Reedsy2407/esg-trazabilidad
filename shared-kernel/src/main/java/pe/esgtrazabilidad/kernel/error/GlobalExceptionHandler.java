package pe.esgtrazabilidad.kernel.error;

import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}

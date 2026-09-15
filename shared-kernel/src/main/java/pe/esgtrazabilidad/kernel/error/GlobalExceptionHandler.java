package pe.esgtrazabilidad.kernel.error;

import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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

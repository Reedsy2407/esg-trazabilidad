package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.passwordreset.service.ResetRateLimitedException;

/** The shared handler cannot add Retry-After; this one answers AUTH-012 with it. */
@RestControllerAdvice(assignableTypes = PasswordResetController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PasswordResetExceptionHandler {

    @ExceptionHandler(ResetRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(ResetRateLimitedException exception) {
        AuthErrors error = AuthErrors.TOO_MANY_RESET_ATTEMPTS;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return ResponseEntity.status(error.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(problemDetail);
    }
}

package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.recycleraccount.service.ActivationRateLimitedException;

/** The shared handler cannot add Retry-After; this one answers AUTH-015 with it. */
@RestControllerAdvice(assignableTypes = RecyclerSessionController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class RecyclerSessionExceptionHandler {

    @ExceptionHandler(ActivationRateLimitedException.class)
    ResponseEntity<ProblemDetail> handleRateLimited(ActivationRateLimitedException exception) {
        AuthErrors error = AuthErrors.TOO_MANY_ACTIVATION_ATTEMPTS;
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(error.getStatus(), error.getMessage());
        problemDetail.setProperty("code", error.getCode());
        return ResponseEntity.status(error.getStatus())
                .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
                .body(problemDetail);
    }
}

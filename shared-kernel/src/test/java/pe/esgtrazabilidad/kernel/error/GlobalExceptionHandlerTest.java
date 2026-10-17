package pe.esgtrazabilidad.kernel.error;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private enum SampleError implements ApplicationError {
        SAMPLE_NOT_FOUND("SMP-001", "Sample not found", HttpStatus.NOT_FOUND);

        private final String code;
        private final String message;
        private final HttpStatus status;

        SampleError(String code, String message, HttpStatus status) {
            this.code = code;
            this.message = message;
            this.status = status;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public HttpStatus getStatus() {
            return status;
        }
    }

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsApplicationExceptionToProblemDetail() {
        ApplicationException exception = new ApplicationException(SampleError.SAMPLE_NOT_FOUND);

        ProblemDetail problemDetail = handler.handleApplicationException(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problemDetail.getDetail()).isEqualTo("Sample not found");
        assertThat(problemDetail.getProperties()).containsEntry("code", "SMP-001");
    }

    @Test
    void mapsMethodArgumentNotValidExceptionToAGenericValidationError() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "ruc", "El RUC debe tener 11 dígitos numéricos"));
        Method dummyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyHandlerMethod", String.class);
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(new MethodParameter(dummyMethod, 0), bindingResult);

        ProblemDetail problemDetail = handler.handleValidationException(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(problemDetail.getDetail()).contains("ruc");
    }

    @Test
    void mapsDataIntegrityViolationExceptionToAGenericConflict() {
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("duplicate key value violates unique constraint");

        ProblemDetail problemDetail = handler.handleDataIntegrityViolation(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "DATA_CONFLICT");
    }

    @Test
    void mapsMethodArgumentTypeMismatchExceptionToAValidationError() throws NoSuchMethodException {
        Method dummyMethod = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyHandlerMethod", String.class);
        MethodArgumentTypeMismatchException exception = new MethodArgumentTypeMismatchException(
                "not-a-uuid", java.util.UUID.class, "associationId", new MethodParameter(dummyMethod, 0), null);

        ProblemDetail problemDetail = handler.handleTypeMismatch(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "VALIDATION_ERROR");
        assertThat(problemDetail.getDetail()).contains("associationId");
    }

    @Test
    void mapsHttpMessageNotReadableExceptionToAValidationError() {
        HttpMessageNotReadableException exception =
                new HttpMessageNotReadableException("JSON parse error: malformed body", (org.springframework.http.HttpInputMessage) null);

        ProblemDetail problemDetail = handler.handleMessageNotReadable(exception);

        assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problemDetail.getProperties()).containsEntry("code", "VALIDATION_ERROR");
    }

    @Test
    void mapsAnUnreachableDatabaseToA503WithAStableCodeRetryAfterAndNoInternalDetail() {
        java.util.List<Exception> causes = java.util.List.of(
                new org.springframework.dao.DataAccessResourceFailureException("Connection to db.internal:5432 refused"),
                new org.springframework.dao.TransientDataAccessResourceException("could not connect to host 10.0.0.7"),
                new org.springframework.dao.QueryTimeoutException("statement timeout: select * from staff_user"),
                new org.springframework.transaction.CannotCreateTransactionException("Could not open JPA EntityManager"));

        for (Exception cause : causes) {
            org.springframework.http.ResponseEntity<ProblemDetail> response = handler.handleUnavailable(cause);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("10");
            ProblemDetail body = response.getBody();
            assertThat(body.getStatus()).isEqualTo(503);
            assertThat(body.getProperties()).containsEntry("code", "SERVICE_UNAVAILABLE");
            assertThat(body.getDetail()).doesNotContain("db.internal", "10.0.0.7", "select", "JPA");
        }
    }

    private void dummyHandlerMethod(String arg) {
    }
}

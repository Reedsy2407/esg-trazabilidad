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

    private void dummyHandlerMethod(String arg) {
    }
}

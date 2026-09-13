package pe.esgtrazabilidad.kernel.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

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
}

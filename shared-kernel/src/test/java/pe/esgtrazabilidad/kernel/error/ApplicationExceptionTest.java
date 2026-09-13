package pe.esgtrazabilidad.kernel.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationExceptionTest {

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

    @Test
    void exposesTheWrappedApplicationError() {
        ApplicationException exception = new ApplicationException(SampleError.SAMPLE_NOT_FOUND);

        assertThat(exception.getError()).isEqualTo(SampleError.SAMPLE_NOT_FOUND);
        assertThat(exception.getMessage()).isEqualTo("Sample not found");
    }
}

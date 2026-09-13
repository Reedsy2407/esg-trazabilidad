package pe.esgtrazabilidad.kernel.error;

import org.springframework.http.HttpStatus;

public interface ApplicationError {

    String getCode();

    String getMessage();

    HttpStatus getStatus();
}

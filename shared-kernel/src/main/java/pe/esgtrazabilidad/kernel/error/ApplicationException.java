package pe.esgtrazabilidad.kernel.error;

public class ApplicationException extends RuntimeException {

    private final ApplicationError error;

    public ApplicationException(ApplicationError error) {
        super(error.getMessage());
        this.error = error;
    }

    public ApplicationError getError() {
        return error;
    }
}

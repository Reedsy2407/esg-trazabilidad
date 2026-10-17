package pe.esgtrazabilidad.auth.email;

/** The reason is a short fixed code (timeout, quota, http_500...), never text from the provider. */
public class EmailSendException extends RuntimeException {

    private final String reason;

    public EmailSendException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}

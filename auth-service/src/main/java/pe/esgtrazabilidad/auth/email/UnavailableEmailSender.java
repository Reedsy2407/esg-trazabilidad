package pe.esgtrazabilidad.auth.email;

import java.time.Instant;

/** {@code EMAIL_PROVIDER=none}: nothing can be sent. Every call fails with the same fixed reason. */
public class UnavailableEmailSender implements EmailSender {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("esg.auth.email");

    @Override
    public void sendResetCode(String to, String code) {
        throw failure();
    }

    @Override
    public void sendPasswordChanged(String to, Instant when) {
        throw failure();
    }

    private static EmailSendException failure() {
        log.warn("event=email_failed provider=none reason=provider_none");
        return new EmailSendException("provider_none");
    }
}

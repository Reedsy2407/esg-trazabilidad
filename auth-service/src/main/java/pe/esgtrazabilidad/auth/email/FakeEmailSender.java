package pe.esgtrazabilidad.auth.email;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * {@code EMAIL_PROVIDER=fake}: keeps what it was asked to send in memory, for the tests and for
 * development. The service refuses it on Render while password recovery is switched on.
 */
public class FakeEmailSender implements EmailSender {

    public record Sent(String kind, String to, String code, Instant when) {}

    private final List<Sent> sent = new CopyOnWriteArrayList<>();
    private volatile boolean failing;

    @Override
    public void sendResetCode(String to, String code) {
        failIfAsked();
        sent.add(new Sent("reset_code", to, code, null));
    }

    @Override
    public void sendPasswordChanged(String to, Instant when) {
        failIfAsked();
        sent.add(new Sent("password_changed", to, null, when));
    }

    public List<Sent> sent() {
        return List.copyOf(sent);
    }

    public Optional<String> lastCodeFor(String to) {
        return sent.stream()
                .filter(s -> s.kind().equals("reset_code") && s.to().equals(to))
                .reduce((first, second) -> second)
                .map(Sent::code);
    }

    /** While true every send fails, like a provider that is down. */
    public void failing(boolean failing) {
        this.failing = failing;
    }

    public void clear() {
        sent.clear();
        failing = false;
    }

    private void failIfAsked() {
        if (failing) {
            throw new EmailSendException("fake_failure");
        }
    }
}

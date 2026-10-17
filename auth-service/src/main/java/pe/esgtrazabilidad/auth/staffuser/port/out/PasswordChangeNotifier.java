package pe.esgtrazabilidad.auth.staffuser.port.out;

import java.time.Instant;

/**
 * Tells the person that their password changed (they changed it, an admin reset it, or they
 * recovered it). Best effort: it never fails the change and never carries the password.
 */
public interface PasswordChangeNotifier {

    void passwordChanged(String email, Instant when);
}

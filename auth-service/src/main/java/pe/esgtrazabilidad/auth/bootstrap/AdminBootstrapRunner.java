package pe.esgtrazabilidad.auth.bootstrap;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

/**
 * Solves the chicken-and-egg problem: nothing can call the token-protected
 * POST /auth/staff-users before any account exists. Gated on table state
 * (existsAny()), not env var presence alone -- a later restart with
 * ADMIN_BOOTSTRAP_EMAIL still configured is a safe no-op, never a reset of
 * an existing account's credentials. The password is never env-supplied or
 * otherwise predictable: SecureRandom-generated here, bcrypt-hashed before
 * being persisted, and logged in plaintext exactly once for the operator to
 * retrieve from this one deploy's own startup log and rotate on first
 * login -- see SPEC-auth-service.md's Resolved Decisions.
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final int PASSWORD_ENTROPY_BYTES = 24;

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String bootstrapEmail;
    private final SecureRandom random = new SecureRandom();

    public AdminBootstrapRunner(
            StaffUserRepository staffUserRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_BOOTSTRAP_EMAIL:}") String bootstrapEmail) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        if (staffUserRepository.existsAny()) {
            return;
        }

        String rawPassword = generateRandomPassword();
        StaffUser admin = StaffUser.create(bootstrapEmail, passwordEncoder.encode(rawPassword), "Administrador");
        staffUserRepository.save(admin);

        // Deliberate: the only place a raw password is ever logged in this
        // codebase. It's the sole way the operator retrieves the bootstrap
        // account's credentials -- nothing else stores or returns it, and
        // this line fires at most once per deployment's lifetime.
        log.warn(
                "Bootstrap staff account created — email={} password={} "
                        + "(cámbiala después del primer login; este mensaje no se repetirá)",
                bootstrapEmail,
                rawPassword);
    }

    private String generateRandomPassword() {
        byte[] entropy = new byte[PASSWORD_ENTROPY_BYTES];
        random.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }
}

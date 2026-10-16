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

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

/**
 * Solves the chicken-and-egg problem: nothing can call the admin-only
 * POST /auth/staff-users before an ADMIN exists. Two cases, both gated on table state and on
 * ADMIN_BOOTSTRAP_EMAIL, never on the variable alone:
 * <ul>
 *   <li>an empty table: creates the first account as ADMIN with a random password, logged in
 *       plaintext exactly once for the operator (and flagged to be changed at the first sign-in);</li>
 *   <li>accounts exist but none is an active ADMIN (the table predates roles, or every admin was
 *       deactivated): promotes the account with that email, if it exists and is active. With an
 *       admin already in place a restart with the variable still set is a safe no-op, never a
 *       reset of anyone's credentials or role.</li>
 * </ul>
 * The password is never env-supplied or otherwise predictable: SecureRandom-generated here,
 * bcrypt-hashed before being persisted -- see SPEC-auth-service.md's Resolved Decisions.
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
            if (staffUserRepository.existsAny() && !staffUserRepository.existsActiveAdmin()) {
                log.warn("No active ADMIN account exists and ADMIN_BOOTSTRAP_EMAIL is not set: "
                        + "nobody can create, deactivate or reset accounts until it names an existing account");
            }
            return;
        }
        if (!staffUserRepository.existsAny()) {
            createFirstAdmin();
        } else if (!staffUserRepository.existsActiveAdmin()) {
            promoteExisting();
        }
    }

    private void createFirstAdmin() {
        String rawPassword = generateRandomPassword();
        // mustChangePassword: nobody but the operator who reads the log knows it, and only once.
        StaffUser admin =
                StaffUser.create(bootstrapEmail, passwordEncoder.encode(rawPassword), "Administrador", StaffRole.ADMIN, true);
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

    private void promoteExisting() {
        staffUserRepository
                .findByEmail(bootstrapEmail.toLowerCase())
                .filter(StaffUser::isActive)
                .ifPresentOrElse(
                        user -> {
                            staffUserRepository.save(user.promoteToAdmin());
                            log.warn("Account {} promoted to ADMIN: no active ADMIN existed", user.getId());
                        },
                        () -> log.warn("ADMIN_BOOTSTRAP_EMAIL names no active account: no ADMIN was promoted"));
    }

    private String generateRandomPassword() {
        byte[] entropy = new byte[PASSWORD_ENTROPY_BYTES];
        random.nextBytes(entropy);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);
    }
}

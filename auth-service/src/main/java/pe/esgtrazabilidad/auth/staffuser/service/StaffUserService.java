package pe.esgtrazabilidad.auth.staffuser.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.GetCurrentStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class StaffUserService implements LoginUseCase, CreateStaffUserUseCase, GetCurrentStaffUserUseCase {

    private static final org.slf4j.Logger hashLog = org.slf4j.LoggerFactory.getLogger("esg.auth");

    private final StaffUserRepository staffUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;

    StaffUserService(StaffUserRepository staffUserRepository, PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer) {
        this.staffUserRepository = staffUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
    }

    /**
     * Not @Transactional: the lookup is one read, and a transaction would hold a pooled database
     * connection (a remote one on Neon) for the whole bcrypt comparison, about 0.6 s on Render's 0.1 CPU.
     */
    @Override
    public String login(String email, String rawPassword) {
        StaffUser staffUser = staffUserRepository
                .findByEmail(email.toLowerCase())
                .filter(StaffUser::isActive)
                .filter(user -> hashMatches(rawPassword, user.getPasswordHash()))
                .orElseThrow(() -> new ApplicationException(AuthErrors.INVALID_CREDENTIALS));
        return jwtIssuer.issue(staffUser.getId(), staffUser.getEmail());
    }

    /** The password check is the one CPU-heavy step of a login: timed on its own (no data in the line). */
    private boolean hashMatches(String rawPassword, String hash) {
        long start = System.nanoTime();
        try {
            return passwordEncoder.matches(rawPassword, hash);
        } finally {
            hashLog.info("event=password_hash service=auth-service durationMs={}", (System.nanoTime() - start) / 1_000_000);
        }
    }

    @Override
    @Transactional
    public StaffUser createStaffUser(CreateStaffUserCommand command) {
        // Checked here, not left to StaffUser.create()'s IllegalArgumentException, which would be a 500.
        if (!StaffUser.isValidEmail(command.email())) {
            throw new ApplicationException(AuthErrors.INVALID_STAFF_EMAIL);
        }
        staffUserRepository.findByEmail(command.email().toLowerCase()).ifPresent(existing -> {
            throw new ApplicationException(AuthErrors.DUPLICATE_STAFF_EMAIL);
        });
        StaffUser staffUser =
                StaffUser.create(command.email(), passwordEncoder.encode(command.rawPassword()), command.fullName());
        return staffUserRepository.save(staffUser);
    }

    @Override
    public StaffUser getCurrent(UUID staffUserId) {
        return staffUserRepository
                .findById(staffUserId)
                .orElseThrow(() -> new ApplicationException(AuthErrors.STAFF_USER_NOT_FOUND));
    }
}

package pe.esgtrazabilidad.auth.staffuser.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.ChangeOwnPasswordUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.GetCurrentStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.ManageStaffAccountsUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class StaffUserService
        implements LoginUseCase,
                CreateStaffUserUseCase,
                GetCurrentStaffUserUseCase,
                ManageStaffAccountsUseCase,
                ChangeOwnPasswordUseCase {

    private static final org.slf4j.Logger hashLog = org.slf4j.LoggerFactory.getLogger("esg.auth");
    // Who did what to whose account; ids only, never an email, a reason or a password.
    private static final org.slf4j.Logger auditLog = org.slf4j.LoggerFactory.getLogger("esg.auth.audit");

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
        String password = PasswordPolicy.normalize(rawPassword);
        StaffUser staffUser = staffUserRepository
                .findByEmail(email.toLowerCase())
                .filter(StaffUser::isActive)
                // An account made before passwords were normalised (NFKC) may hold the raw form.
                .filter(user -> hashMatches(password, user.getPasswordHash())
                        || (!password.equals(rawPassword) && hashMatches(rawPassword, user.getPasswordHash())))
                .orElseThrow(() -> new ApplicationException(AuthErrors.INVALID_CREDENTIALS));
        return jwtIssuer.issue(
                staffUser.getId(), staffUser.getEmail(), staffUser.getRole(), staffUser.isMustChangePassword());
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
        requireAdmin(command.actorId());
        // Checked here, not left to StaffUser.create()'s IllegalArgumentException, which would be a 500.
        if (!StaffUser.isValidEmail(command.email())) {
            throw new ApplicationException(AuthErrors.INVALID_STAFF_EMAIL);
        }
        String password = PasswordPolicy.normalize(command.rawPassword());
        PasswordPolicy.validate(password, command.email());
        staffUserRepository.findByEmail(command.email().toLowerCase()).ifPresent(existing -> {
            throw new ApplicationException(AuthErrors.DUPLICATE_STAFF_EMAIL);
        });
        StaffRole role = command.role() == null ? StaffRole.STAFF : command.role();
        // Somebody else chose this password: the new person is asked to pick their own at the first sign-in.
        StaffUser staffUser =
                StaffUser.create(command.email(), passwordEncoder.encode(password), command.fullName(), role, true);
        StaffUser saved = staffUserRepository.save(staffUser);
        auditLog.info("event=account_created actor={} target={} role={}", command.actorId(), saved.getId(), role);
        return saved;
    }

    @Override
    public StaffUser getCurrent(UUID staffUserId) {
        return staffUserRepository
                .findById(staffUserId)
                .orElseThrow(() -> new ApplicationException(AuthErrors.STAFF_USER_NOT_FOUND));
    }

    @Override
    public List<StaffUser> list(UUID actorId) {
        requireAdmin(actorId);
        return staffUserRepository.findAll();
    }

    @Override
    @Transactional
    public StaffUser deactivate(UUID actorId, UUID targetId, String reason) {
        // Serialises admins deactivating each other: the second one waits here and then finds
        // itself deactivated (AUTH-006) instead of both passing the check at the same time.
        staffUserRepository.lockActiveAdmins();
        requireAdmin(actorId);
        StaffUser target = otherAccount(actorId, targetId);
        if (!target.isActive()) {
            throw new ApplicationException(AuthErrors.STATUS_UNCHANGED);
        }
        // The admin is active, is not the target and the admins are locked, so at least one active
        // admin always remains.
        StaffUser saved = staffUserRepository.save(target.deactivate(reason.strip(), actorId, Instant.now()));
        auditLog.info("event=account_deactivated actor={} target={}", actorId, targetId);
        return saved;
    }

    @Override
    @Transactional
    public StaffUser reactivate(UUID actorId, UUID targetId, String reason) {
        requireAdmin(actorId);
        StaffUser target = otherAccount(actorId, targetId);
        if (target.isActive()) {
            throw new ApplicationException(AuthErrors.STATUS_UNCHANGED);
        }
        StaffUser saved = staffUserRepository.save(target.reactivate(reason.strip(), actorId, Instant.now()));
        auditLog.info("event=account_reactivated actor={} target={}", actorId, targetId);
        return saved;
    }

    /** Not @Transactional for the same reason as login: bcrypt must not hold a connection. */
    @Override
    public void resetPassword(UUID actorId, UUID targetId, String newRawPassword) {
        requireAdmin(actorId);
        StaffUser target = otherAccount(actorId, targetId);
        String password = PasswordPolicy.normalize(newRawPassword);
        PasswordPolicy.validate(password, target.getEmail());
        // Only the password columns are written: another admin may have changed the account meanwhile.
        staffUserRepository.updatePassword(targetId, passwordEncoder.encode(password), true, Instant.now());
        auditLog.info("event=password_reset_by_admin actor={} target={}", actorId, targetId);
    }

    /** Not @Transactional: two bcrypt runs (compare, encode) must not hold a connection. */
    @Override
    public void changeOwnPassword(UUID staffUserId, String currentRawPassword, String newRawPassword) {
        StaffUser staffUser = staffUserRepository
                .findById(staffUserId)
                .filter(StaffUser::isActive)
                .orElseThrow(() -> new ApplicationException(AuthErrors.STAFF_USER_NOT_FOUND));
        String current = PasswordPolicy.normalize(currentRawPassword);
        // Same fallback as the login: an account made before NFKC may hold the raw form.
        if (!hashMatches(current, staffUser.getPasswordHash())
                && !(!current.equals(currentRawPassword) && hashMatches(currentRawPassword, staffUser.getPasswordHash()))) {
            throw new ApplicationException(AuthErrors.WRONG_CURRENT_PASSWORD);
        }
        String password = PasswordPolicy.normalize(newRawPassword);
        PasswordPolicy.validate(password, staffUser.getEmail());
        if (password.equals(PasswordPolicy.normalize(currentRawPassword))) {
            throw new ApplicationException(AuthErrors.WEAK_PASSWORD);
        }
        staffUserRepository.updatePassword(staffUserId, passwordEncoder.encode(password), false, Instant.now());
        auditLog.info("event=password_changed actor={}", staffUserId);
    }

    /** From the database, not the token: a deactivated or demoted admin loses the power at once. */
    private void requireAdmin(UUID actorId) {
        boolean admin = actorId != null
                && staffUserRepository
                        .findById(actorId)
                        .filter(StaffUser::isActive)
                        .filter(StaffUser::isAdmin)
                        .isPresent();
        if (!admin) {
            throw new ApplicationException(AuthErrors.ADMIN_REQUIRED);
        }
    }

    private StaffUser otherAccount(UUID actorId, UUID targetId) {
        if (actorId.equals(targetId)) {
            throw new ApplicationException(AuthErrors.CANNOT_ACT_ON_OWN_ACCOUNT);
        }
        return staffUserRepository
                .findById(targetId)
                .orElseThrow(() -> new ApplicationException(AuthErrors.STAFF_USER_NOT_FOUND));
    }
}

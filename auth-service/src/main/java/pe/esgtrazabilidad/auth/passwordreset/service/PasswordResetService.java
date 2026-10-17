package pe.esgtrazabilidad.auth.passwordreset.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.passwordreset.port.in.PasswordResetUseCase;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.CheckResult;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.Kind;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.Outcome;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.PasswordChangeNotifier;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.auth.staffuser.service.PasswordPolicy;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * Password recovery by an 8-digit code sent to the account's email (DISENO-OLVIDE-CONTRASENA.md).
 *
 * <p>{@code request} always does the same steps (purge, limits, lookup, HMAC, a write) and answers
 * the same, whether the account exists, is active, is limited or the daily cap is reached; only the
 * caller's own address can be refused (429), because that says nothing about an account. The email goes
 * out on another thread. {@code confirm} checks the new password first (a pure function of the input,
 * no oracle), then the code with a bounded number of attempts, then spends the code and writes the
 * password in one transaction, so two simultaneous confirmations cannot both win. The code check commits
 * on its own so a failed attempt is counted even though the request ends in an error.
 */
@Service
class PasswordResetService implements PasswordResetUseCase, PasswordChangeNotifier {

    // Limits (DISENO-OLVIDE-CONTRASENA.md 4.1).
    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int REQUESTS_PER_EMAIL_PER_HOUR = 3;
    static final Duration MIN_GAP_PER_EMAIL = Duration.ofSeconds(60);
    static final int REQUESTS_PER_IP_PER_HOUR = 10;
    static final int CONFIRMS_PER_IP_PER_HOUR = 20;
    // Under Apps Script's quota of 100 recipients a day for a consumer account.
    static final int DAILY_SEND_CAP = 80;
    private static final Duration HOUR = Duration.ofHours(1);
    private static final Duration DAY = Duration.ofHours(24);
    private static final UUID NO_ACCOUNT = new UUID(0L, 0L);

    private static final Logger audit = LoggerFactory.getLogger("esg.auth.audit");

    private final StaffUserRepository staffUsers;
    private final PasswordResetStore store;
    private final CodeHasher hasher;
    private final CodeGenerator generator;
    private final EmailDispatcher dispatcher;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final boolean enabled;

    PasswordResetService(
            StaffUserRepository staffUsers,
            PasswordResetStore store,
            CodeHasher hasher,
            CodeGenerator generator,
            EmailDispatcher dispatcher,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transaction,
            Clock clock,
            @Value("${PASSWORD_RESET_EMAIL_ENABLED}") boolean enabled) {
        this.staffUsers = staffUsers;
        this.store = store;
        this.hasher = hasher;
        this.generator = generator;
        this.dispatcher = dispatcher;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public void request(String rawEmail, String clientIp) {
        Instant now = clock.instant();
        String email = normalize(rawEmail);
        String emailHmac = hasher.label("email", email);
        String ipHmac = hasher.label("ip", clientIp);
        store.purgeOlderThan(now.minus(DAY));

        List<Instant> fromThisAddress = store.acceptedByIpSince(Kind.REQUEST, ipHmac, now.minus(HOUR));
        if (fromThisAddress.size() >= REQUESTS_PER_IP_PER_HOUR) {
            audit.info("event=password_reset_throttled scope=ip");
            throw new ResetRateLimitedException(retryAfter(fromThisAddress, now));
        }

        List<Instant> forThisEmail = store.acceptedByEmailSince(Kind.REQUEST, emailHmac, now.minus(HOUR));
        boolean emailLimited = forThisEmail.size() >= REQUESTS_PER_EMAIL_PER_HOUR
                || forThisEmail.stream().anyMatch(at -> at.isAfter(now.minus(MIN_GAP_PER_EMAIL)));
        boolean capped = store.sentSince(now.minus(DAY)) >= DAILY_SEND_CAP;
        UUID requestId = store.recordRequest(Kind.REQUEST, emailHmac, ipHmac, now, !emailLimited);

        Optional<StaffUser> account = staffUsers.findByEmail(email).filter(StaffUser::isActive);
        UUID codeId = IdGenerator.generate();
        String code = generator.next();
        String hmac = hasher.code(account.map(StaffUser::getId).orElse(NO_ACCOUNT), codeId, code);

        boolean send = enabled && !emailLimited && !capped && account.isPresent();
        if (send) {
            try {
                store.replaceCode(codeId, account.get().getId(), hmac, now, now.plus(CODE_LIFETIME));
            } catch (DataIntegrityViolationException simultaneousRequest) {
                // Another request for the same account stored its code first: that one is the live code.
                send = false;
            }
        }
        if (send) {
            dispatcher.resetCode(requestId, account.get().getEmail(), code);
            audit.info("event=password_reset_requested subject={}", account.get().getId());
        } else {
            store.replaceCodeDecoy();
            audit.info(
                    "event=password_reset_requested subject={} sent=false reason={}",
                    account.map(user -> user.getId().toString()).orElse(emailHmac.substring(0, 16)),
                    !enabled ? "disabled" : capped ? "daily_cap" : emailLimited ? "email_limit" : "no_account");
        }
    }

    @Override
    public void confirm(String rawEmail, String rawCode, String newRawPassword, String clientIp) {
        Instant now = clock.instant();
        String email = normalize(rawEmail);
        String ipHmac = hasher.label("ip", clientIp);

        List<Instant> fromThisAddress = store.acceptedByIpSince(Kind.CONFIRM, ipHmac, now.minus(HOUR));
        if (fromThisAddress.size() >= CONFIRMS_PER_IP_PER_HOUR) {
            audit.info("event=password_reset_throttled scope=ip");
            throw new ResetRateLimitedException(retryAfter(fromThisAddress, now));
        }
        store.recordRequest(Kind.CONFIRM, hasher.label("email", email), ipHmac, now, true);

        // A pure function of what was typed: it says nothing about the code or the account.
        String password = PasswordPolicy.normalize(newRawPassword);
        PasswordPolicy.validate(password, email);

        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        Optional<StaffUser> account = staffUsers.findByEmail(email).filter(StaffUser::isActive);
        if (account.isEmpty() || !code.matches("\\d{" + CodeGenerator.LENGTH + "}")) {
            hasher.code(NO_ACCOUNT, NO_ACCOUNT, code); // the same hashing work as a real attempt
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        StaffUser user = account.get();
        CheckResult checked = store.check(
                user.getId(),
                now,
                MAX_CODE_ATTEMPTS,
                live -> hasher.matches(user.getId(), live.id(), code, live.codeHmac()));
        if (checked.outcome() != Outcome.MATCH) {
            audit.info("event=password_reset_code_failed subject={} outcome={}", user.getId(), checked.outcome());
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        if (repeatsCurrent(password, newRawPassword, user.getPasswordHash())) {
            throw new ApplicationException(AuthErrors.WEAK_PASSWORD);
        }

        // BCrypt runs before the transaction so it does not hold a connection (about 0.6 s on Render).
        String hash = passwordEncoder.encode(password);
        Boolean changed = transaction.execute(status -> {
            if (!store.consume(checked.codeId(), now)) {
                return false;
            }
            staffUsers.updatePassword(user.getId(), hash, false, now);
            return true;
        });
        if (!Boolean.TRUE.equals(changed)) {
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        audit.info("event=password_reset_confirmed subject={}", user.getId());
        passwordChanged(user.getEmail(), now);
    }

    /** The notice after any password change; it respects the same switch and the same daily cap. */
    @Override
    public void passwordChanged(String email, Instant when) {
        if (!enabled) {
            return;
        }
        try {
            if (store.sentSince(when.minus(DAY)) >= DAILY_SEND_CAP) {
                audit.info("event=password_notice_skipped reason=daily_cap");
                return;
            }
            UUID id = store.recordRequest(
                    Kind.NOTICE, hasher.label("email", normalize(email)), hasher.label("ip", "-"), when, true);
            dispatcher.passwordChanged(id, email, when);
        } catch (RuntimeException failure) {
            // A notice must never undo or fail the change it reports.
            audit.warn("event=password_notice_failed type={}", failure.getClass().getSimpleName());
        }
    }

    private boolean repeatsCurrent(String normalized, String raw, String currentHash) {
        return passwordEncoder.matches(normalized, currentHash)
                || (!normalized.equals(raw) && passwordEncoder.matches(raw, currentHash));
    }

    private static long retryAfter(List<Instant> inWindow, Instant now) {
        // The oldest attempt leaves the window an hour after it happened.
        return Duration.between(now, inWindow.get(0).plus(HOUR)).toSeconds() + 1;
    }

    private static String normalize(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}

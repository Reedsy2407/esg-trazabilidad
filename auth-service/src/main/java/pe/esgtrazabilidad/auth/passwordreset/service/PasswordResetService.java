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
 * out on another thread. {@code verify} checks the code with a bounded number of attempts (every failure
 * does the same work and answers the same), spends it and issues a one-use reset token kept as an HMAC;
 * the check commits on its own so a failed attempt is counted even though the request ends in an error.
 * {@code confirm} finds the live token, checks the new password (a refused one does not spend the token),
 * then spends the token and writes the password in one transaction, so two simultaneous confirmations
 * cannot both win.
 */
@Service
class PasswordResetService implements PasswordResetUseCase, PasswordChangeNotifier {

    // Limits (DISENO-OLVIDE-CONTRASENA.md 4.1).
    static final Duration CODE_LIFETIME = Duration.ofMinutes(10);
    static final int MAX_CODE_ATTEMPTS = 5;
    static final int REQUESTS_PER_EMAIL_PER_HOUR = 3;
    static final Duration MIN_GAP_PER_EMAIL = Duration.ofSeconds(60);
    static final int REQUESTS_PER_IP_PER_HOUR = 10;
    static final int VERIFIES_PER_IP_PER_HOUR = 20;
    static final int CONFIRMS_PER_IP_PER_HOUR = 20;
    static final Duration TOKEN_LIFETIME = Duration.ofMinutes(10);
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
    public String verify(String rawEmail, String rawCode, String clientIp) {
        Instant now = clock.instant();
        String email = normalize(rawEmail);
        String ipHmac = hasher.label("ip", clientIp);

        List<Instant> fromThisAddress = store.acceptedByIpSince(Kind.VERIFY, ipHmac, now.minus(HOUR));
        if (fromThisAddress.size() >= VERIFIES_PER_IP_PER_HOUR) {
            audit.info("event=password_reset_throttled scope=ip");
            throw new ResetRateLimitedException(retryAfter(fromThisAddress, now));
        }
        store.recordRequest(Kind.VERIFY, hasher.label("email", email), ipHmac, now, true);

        // Every failure runs these same steps (lookup, check under lock, one update, one HMAC), so an
        // unknown, inactive or code-less account cannot be told from a wrong code by what it costs.
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s", "");
        Optional<StaffUser> account = staffUsers.findByEmail(email).filter(StaffUser::isActive);
        UUID accountId = account.map(StaffUser::getId).orElse(NO_ACCOUNT);
        CheckResult checked = store.check(
                accountId,
                now,
                MAX_CODE_ATTEMPTS,
                live -> hasher.matches(accountId, live.id(), code, live.codeHmac()));
        if (checked.outcome() == Outcome.NONE) {
            hasher.code(NO_ACCOUNT, NO_ACCOUNT, code); // the HMAC a real comparison would have cost
        }
        if (account.isEmpty() || checked.outcome() != Outcome.MATCH) {
            audit.info("event=password_reset_code_failed outcome={}", checked.outcome());
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }

        UUID tokenId = IdGenerator.generate();
        String secret = generator.tokenSecret();
        String tokenHmac = hasher.token(accountId, tokenId, secret);
        Boolean issued = transaction.execute(status -> {
            if (!store.consume(checked.codeId(), now)) {
                return false; // somebody else spent the code between the check and here
            }
            store.replaceToken(tokenId, accountId, tokenHmac, now, now.plus(TOKEN_LIFETIME));
            return true;
        });
        if (!Boolean.TRUE.equals(issued)) {
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        audit.info("event=password_reset_code_verified subject={}", accountId);
        return tokenId + "." + secret;
    }

    @Override
    public void confirm(String resetToken, String newRawPassword, String clientIp) {
        Instant now = clock.instant();
        String ipHmac = hasher.label("ip", clientIp);

        List<Instant> fromThisAddress = store.acceptedByIpSince(Kind.CONFIRM, ipHmac, now.minus(HOUR));
        if (fromThisAddress.size() >= CONFIRMS_PER_IP_PER_HOUR) {
            audit.info("event=password_reset_throttled scope=ip");
            throw new ResetRateLimitedException(retryAfter(fromThisAddress, now));
        }
        store.recordRequest(Kind.CONFIRM, hasher.label("email", "-"), ipHmac, now, true);

        StaffUser user = redeemable(resetToken, now);

        // The token is not spent by a password the policy refuses: the person can type another one.
        String password = PasswordPolicy.normalize(newRawPassword);
        PasswordPolicy.validate(password, user.getEmail());
        if (repeatsCurrent(password, newRawPassword, user.getPasswordHash())) {
            throw new ApplicationException(AuthErrors.WEAK_PASSWORD);
        }

        // BCrypt runs before the transaction so it does not hold a connection (about 0.6 s on Render).
        String hash = passwordEncoder.encode(password);
        UUID tokenId = tokenId(resetToken);
        Boolean changed = transaction.execute(status -> {
            if (!store.consumeToken(tokenId, now)) {
                return false;
            }
            staffUsers.updatePassword(user.getId(), hash, false, now);
            store.spendLiveCodes(user.getId(), now);
            return true;
        });
        if (!Boolean.TRUE.equals(changed)) {
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        audit.info("event=password_reset_confirmed subject={}", user.getId());
        passwordChanged(user.getEmail(), now);
    }

    /** The active account a live, matching token belongs to; AUTH-011 for anything else. */
    private StaffUser redeemable(String resetToken, Instant now) {
        int dot = resetToken == null ? -1 : resetToken.indexOf('.');
        UUID tokenId = dot < 0 ? null : parseUuid(resetToken.substring(0, dot));
        String secret = dot < 0 ? "" : resetToken.substring(dot + 1);
        Optional<PasswordResetStore.LiveToken> live =
                tokenId == null ? Optional.empty() : store.findLiveToken(tokenId, now);
        UUID accountId = live.map(PasswordResetStore.LiveToken::staffUserId).orElse(NO_ACCOUNT);
        UUID id = tokenId == null ? NO_ACCOUNT : tokenId;
        boolean matches = hasher.tokenMatches(accountId, id, secret, live.map(PasswordResetStore.LiveToken::tokenHmac).orElse(""));
        Optional<StaffUser> user = matches ? staffUsers.findById(accountId).filter(StaffUser::isActive) : Optional.empty();
        if (user.isEmpty()) {
            throw new ApplicationException(AuthErrors.INVALID_RESET_CODE);
        }
        return user.get();
    }

    private static UUID tokenId(String resetToken) {
        return UUID.fromString(resetToken.substring(0, resetToken.indexOf('.')));
    }

    private static UUID parseUuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException malformed) {
            return null;
        }
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

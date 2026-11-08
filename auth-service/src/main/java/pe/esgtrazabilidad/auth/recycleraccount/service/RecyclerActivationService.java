package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Clock;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerActivationUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.ActivationAttemptStore;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerDeviceStore;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatusCopy;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.auth.retention.RetentionConfigRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * Trades a code for a session. Order matters: the address is counted first (so a flood of guesses is cut
 * before it touches anything else), then the typed code's own failure count, and only then is the code
 * looked up. Every way of not having a valid code (wrong, expired, replaced, used, malformed) answers the
 * same AUTH-014, so the caller learns nothing about which. The claim itself is one transaction: the code
 * row is locked, so two requests with the same code cannot both succeed.
 *
 * <p>Nothing here logs the code, its HMAC, an address or a token.
 */
@Service
class RecyclerActivationService implements RecyclerActivationUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecyclerActivationService.class);

    private final ActivationAttemptStore attempts;
    private final ActivationLimits limits;
    private final RecyclerAccountStore accounts;
    private final RecyclerDeviceStore devices;
    private final RecyclerStatusCopyRepository statusCopies;
    private final RetentionConfigRepository retention;
    private final RecyclerCodeHasher hasher;
    private final RefreshTokens refreshTokens;
    private final RecyclerAccessTokenIssuer accessTokens;
    private final TransactionTemplate transaction;
    private final Clock clock;

    RecyclerActivationService(
            ActivationAttemptStore attempts,
            ActivationLimits limits,
            RecyclerAccountStore accounts,
            RecyclerDeviceStore devices,
            RecyclerStatusCopyRepository statusCopies,
            RetentionConfigRepository retention,
            RecyclerCodeHasher hasher,
            RefreshTokens refreshTokens,
            RecyclerAccessTokenIssuer accessTokens,
            TransactionTemplate transaction,
            Clock clock) {
        this.attempts = attempts;
        this.limits = limits;
        this.accounts = accounts;
        this.devices = devices;
        this.statusCopies = statusCopies;
        this.retention = retention;
        this.hasher = hasher;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public ActivationResult activate(String code, String deviceLabel, String deviceInstallId, String clientAddress) {
        Instant now = clock.instant();

        OptionalLong addressWait = attempts.takeAddressSlot(
                hasher.address(clientAddress), now, limits.maxPerAddress(), limits.addressWindow());
        if (addressWait.isPresent()) {
            log.warn("event=recycler_activation_limited scope=address");
            throw new ActivationRateLimitedException(addressWait.getAsLong());
        }

        String normalised = ActivationCodes.normalise(code);
        // A typed string that cannot be a code has no stable key to count failures under; the address
        // limit above is what bounds those.
        String codeHmac = normalised == null ? null : hasher.code(normalised);
        if (codeHmac != null) {
            OptionalLong codeWait =
                    attempts.codeBlockedFor(codeHmac, now, limits.maxFailedPerCode(), limits.codeBlock());
            if (codeWait.isPresent()) {
                log.warn("event=recycler_activation_limited scope=code");
                throw new ActivationRateLimitedException(codeWait.getAsLong());
            }
        }

        ActivationResult result = codeHmac == null
                ? null
                : transaction.execute(status -> claim(codeHmac, deviceLabel, deviceInstallId, now));
        if (result == null) {
            if (codeHmac != null) {
                attempts.recordCodeFailure(codeHmac, now);
            }
            log.warn("event=recycler_activation_failed reason=invalid_code");
            throw new ApplicationException(AuthErrors.INVALID_ACTIVATION_CODE);
        }
        return result;
    }

    /** Null when there is no live code with that HMAC. */
    private ActivationResult claim(String codeHmac, String deviceLabel, String installId, Instant now) {
        UUID accountId = accounts.lockLiveCode(codeHmac, now).orElse(null);
        if (accountId == null) {
            return null;
        }
        RecyclerAccount account = accounts.findById(accountId).orElseThrow();
        // A recycler turned off (or never synced) gets no session; the code stays unused so it still works
        // if the recycler is reactivated within its 72 hours.
        RecyclerStatusCopy copy = statusCopies
                .find(account.recyclerId())
                .filter(RecyclerStatusCopy::isActive)
                .orElseThrow(() -> new ApplicationException(AuthErrors.RECYCLER_INACTIVE));

        accounts.markCodeUsed(codeHmac, now);
        accounts.markActivated(account.id(), now);

        UUID deviceId = IdGenerator.generate();
        String refreshToken = refreshTokens.derive(deviceId, 0);
        devices.insert(
                deviceId,
                account.id(),
                deviceLabel.strip(),
                installId.strip(),
                RefreshTokens.hash(refreshToken),
                now,
                now.plus(SessionPolicy.ABSOLUTE));

        int consentVersion = retention.get().consentVersion();
        String accessToken = accessTokens.issue(account, copy.associationId(), deviceId, consentVersion);
        log.info("event=recycler_activated accountId={} deviceId={}", account.id(), deviceId);
        return new ActivationResult(
                accessToken,
                refreshToken,
                RecyclerAccessTokenIssuer.LIFETIME.toSeconds(),
                !RecyclerAccessTokenIssuer.hasCurrentConsent(account, consentVersion),
                consentVersion);
    }
}

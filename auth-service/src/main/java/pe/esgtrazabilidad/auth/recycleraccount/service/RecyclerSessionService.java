package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerDevice;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerSessionUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerDeviceStore;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatusCopy;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.auth.retention.RetentionConfigRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * Rotating refresh with a grace window (A.3). The device row is locked for the whole decision, so two
 * refreshes of one device are serialised: the second sees the first's rotation and, holding the old
 * token, falls into the grace window instead of racing it. A refusal that must leave a trace (the
 * revocation on reuse) is returned, not thrown, so its transaction commits; the exception is thrown
 * after. Live status is read from the status copy on every refresh, and a recycler with no copy is not
 * active (fails closed).
 *
 * <p>Nothing here logs a token, a hash or an address.
 */
@Service
class RecyclerSessionService implements RecyclerSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecyclerSessionService.class);

    /** Either a result or the error to throw once the transaction has committed. */
    private record Outcome(RefreshResult result, AuthErrors error) {
        static Outcome ok(RefreshResult result) {
            return new Outcome(result, null);
        }

        static Outcome fail(AuthErrors error) {
            return new Outcome(null, error);
        }
    }

    private final RecyclerDeviceStore devices;
    private final RecyclerAccountStore accounts;
    private final RecyclerStatusCopyRepository statusCopies;
    private final RetentionConfigRepository retention;
    private final RefreshTokens refreshTokens;
    private final RecyclerAccessTokenIssuer accessTokens;
    private final TransactionTemplate transaction;
    private final Clock clock;

    RecyclerSessionService(
            RecyclerDeviceStore devices,
            RecyclerAccountStore accounts,
            RecyclerStatusCopyRepository statusCopies,
            RetentionConfigRepository retention,
            RefreshTokens refreshTokens,
            RecyclerAccessTokenIssuer accessTokens,
            TransactionTemplate transaction,
            Clock clock) {
        this.devices = devices;
        this.accounts = accounts;
        this.statusCopies = statusCopies;
        this.retention = retention;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.transaction = transaction;
        this.clock = clock;
    }

    @Override
    public RefreshResult refresh(String refreshToken) {
        Instant now = clock.instant();
        Outcome outcome = transaction.execute(status -> decide(refreshToken, now));
        if (outcome.error() != null) {
            throw new ApplicationException(outcome.error());
        }
        return outcome.result();
    }

    private Outcome decide(String refreshToken, Instant now) {
        String hash = RefreshTokens.hash(refreshToken);
        RecyclerDevice device = devices.findByTokenHashForUpdate(hash).orElse(null);
        if (device == null) {
            return Outcome.fail(AuthErrors.REFRESH_INVALID);
        }
        if (device.isRevoked()) {
            return Outcome.fail(AuthErrors.DEVICE_REVOKED);
        }
        if (!now.isBefore(device.absoluteExpiresAt()) || !now.isBefore(device.lastSeenAt().plus(SessionPolicy.INACTIVITY))) {
            return Outcome.fail(AuthErrors.REFRESH_INVALID);
        }

        boolean isCurrent = RecyclerCodeHasher.sameValue(device.refreshHash(), hash);
        if (!isCurrent && (device.previousValidUntil() == null || now.isAfter(device.previousValidUntil()))) {
            // An old token used after its grace window: it was copied or replayed. The whole device goes,
            // current token included, and the revocation is committed with this answer.
            devices.revoke(device.id(), now);
            log.warn("event=recycler_device_revoked reason=refresh_reuse deviceId={}", device.id());
            return Outcome.fail(AuthErrors.DEVICE_REVOKED);
        }

        RecyclerAccount account = accounts.findById(device.accountId()).orElse(null);
        RecyclerStatusCopy copy = account == null
                ? null
                : statusCopies.find(account.recyclerId()).filter(RecyclerStatusCopy::isActive).orElse(null);
        if (copy == null) {
            log.warn("event=recycler_refresh_refused reason=inactive deviceId={}", device.id());
            return Outcome.fail(AuthErrors.RECYCLER_INACTIVE);
        }

        String newToken;
        if (isCurrent) {
            int generation = device.refreshGeneration() + 1;
            newToken = refreshTokens.derive(device.id(), generation);
            devices.rotate(
                    device.id(), RefreshTokens.hash(newToken), generation, device.refreshHash(), now.plus(SessionPolicy.GRACE), now);
        } else {
            // The previous token inside its window: the same rotation as before, nothing new is minted.
            // The derived token must be the one stored; if the derivation key changed since, it is not,
            // and the session is refused visibly instead of handing out a token that would die unseen.
            newToken = refreshTokens.derive(device.id(), device.refreshGeneration());
            if (!RecyclerCodeHasher.sameValue(RefreshTokens.hash(newToken), device.refreshHash())) {
                log.error("event=recycler_refresh_refused reason=derivation_mismatch deviceId={}", device.id());
                return Outcome.fail(AuthErrors.REFRESH_INVALID);
            }
            // Only inside the 10 minute window, so renewing last_seen here cannot stretch a session.
            devices.touch(device.id(), now);
        }
        String accessToken =
                accessTokens.issue(account, copy.associationId(), device.id(), retention.get().consentVersion());
        return Outcome.ok(new RefreshResult(accessToken, newToken, RecyclerAccessTokenIssuer.LIFETIME.toSeconds()));
    }

    @Override
    public void logout(UUID accountId, String refreshToken) {
        Instant now = clock.instant();
        Boolean found = transaction.execute(status -> {
            RecyclerDevice device = devices.findByTokenHashForUpdate(RefreshTokens.hash(refreshToken)).orElse(null);
            // A token that is not one of the caller's own devices is answered like an unknown one.
            if (device == null || !device.accountId().equals(accountId)) {
                return false;
            }
            devices.revoke(device.id(), now);
            log.info("event=recycler_logout deviceId={}", device.id());
            return true;
        });
        if (!Boolean.TRUE.equals(found)) {
            throw new ApplicationException(AuthErrors.REFRESH_INVALID);
        }
    }
}

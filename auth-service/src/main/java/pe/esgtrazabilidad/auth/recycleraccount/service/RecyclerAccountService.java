package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountStatus;
import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.IssuedCode;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;
import pe.esgtrazabilidad.auth.recycleraccount.exception.RecyclerAccountErrors;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerAccountUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatusCopy;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

@Service
class RecyclerAccountService implements RecyclerAccountUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecyclerAccountService.class);

    /** R3: a code lives 72 hours. */
    static final Duration CODE_LIFETIME = Duration.ofHours(72);

    private final RecyclerAccountStore accounts;
    private final RecyclerStatusCopyRepository statusCopies;
    private final ActivationCodes codes;
    private final RecyclerCodeHasher hasher;
    private final Clock clock;

    RecyclerAccountService(
            RecyclerAccountStore accounts,
            RecyclerStatusCopyRepository statusCopies,
            ActivationCodes codes,
            RecyclerCodeHasher hasher,
            Clock clock) {
        this.accounts = accounts;
        this.statusCopies = statusCopies;
        this.codes = codes;
        this.hasher = hasher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public IssuedCode createAccountAndCode(UUID recyclerId) {
        RecyclerStatusCopy copy = activeCopyOf(recyclerId);
        Instant now = clock.instant();
        RecyclerAccount account = accounts.findByRecyclerId(recyclerId).orElse(null);
        if (account == null) {
            RecyclerAccount fresh = RecyclerAccount.pending(IdGenerator.generate(), recyclerId, copy.associationId(), now);
            // Two people creating at once: one inserts, the other reads what won.
            account = accounts.insertIfAbsent(fresh) ? fresh : accounts.findByRecyclerId(recyclerId).orElseThrow();
        }
        if (account.status() == AccountStatus.ACTIVE) {
            // Already activated: a new code is a reissue, which has its own rule (every device revoked).
            throw new ApplicationException(RecyclerAccountErrors.ACCOUNT_ALREADY_ACTIVE);
        }
        return issueCode(account, now);
    }

    @Override
    @Transactional
    public IssuedCode reissueCode(UUID accountId) {
        RecyclerAccount account =
                accounts.findByIdForUpdate(accountId).orElseThrow(() -> new ApplicationException(RecyclerAccountErrors.ACCOUNT_NOT_FOUND));
        activeCopyOf(account.recyclerId());
        if (account.status() == AccountStatus.ACTIVE && accounts.liveDeviceCount(account.id()) > 0) {
            throw new ApplicationException(RecyclerAccountErrors.ACCOUNT_ALREADY_ACTIVE);
        }
        return issueCode(account, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountSummary> list(UUID associationId) {
        return accounts.list(associationId);
    }

    /** AUTH-020 with no copy yet (never a made-up state), AUTH-018 when the recycler is turned off. */
    private RecyclerStatusCopy activeCopyOf(UUID recyclerId) {
        RecyclerStatusCopy copy = statusCopies
                .find(recyclerId)
                .orElseThrow(() -> new ApplicationException(AuthErrors.RECYCLER_NOT_SYNCED));
        if (!copy.isActive()) {
            throw new ApplicationException(AuthErrors.RECYCLER_INACTIVE);
        }
        return copy;
    }

    private IssuedCode issueCode(RecyclerAccount account, Instant now) {
        String code = codes.next();
        Instant expiresAt = now.plus(CODE_LIFETIME);
        accounts.replaceCode(account.id(), IdGenerator.generate(), hasher.code(code), now, expiresAt);
        // The code itself is never logged.
        log.info("event=recycler_code_issued accountId={} expiresAt={}", account.id(), expiresAt);
        return new IssuedCode(account.id(), code, expiresAt);
    }
}

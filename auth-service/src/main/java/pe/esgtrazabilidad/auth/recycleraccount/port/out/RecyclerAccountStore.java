package pe.esgtrazabilidad.auth.recycleraccount.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;

/** Accounts and their activation codes. Callers run inside the service's transaction. */
public interface RecyclerAccountStore {

    Optional<RecyclerAccount> findById(UUID accountId);

    /** Same, locking the row until the transaction ends: checks that follow cannot be overtaken by an activation. */
    Optional<RecyclerAccount> findByIdForUpdate(UUID accountId);

    Optional<RecyclerAccount> findByRecyclerId(UUID recyclerId);

    /** Inserts unless the recycler already has an account (it has one at most). True if it inserted. */
    boolean insertIfAbsent(RecyclerAccount account);

    /**
     * Locks the account row, spends the live code (if any) and stores the new one as an HMAC. The lock
     * serialises two people asking for a code for the same account at once.
     */
    void replaceCode(UUID accountId, UUID codeId, String codeHmac, Instant now, Instant expiresAt);

    /**
     * The live code with this HMAC (not used, not replaced, not expired at {@code now}), locking its row so
     * two activations of the same code cannot both succeed. Returns the account id it belongs to.
     */
    Optional<UUID> lockLiveCode(String codeHmac, Instant now);

    void markCodeUsed(String codeHmac, Instant now);

    /** PENDING_ACTIVATION to ACTIVE; keeps the first activation time if it was already active. */
    void markActivated(UUID accountId, Instant now);

    /**
     * What the person answered to the consent. {@code consentVersion} null means they did not accept (any
     * earlier acceptance is withdrawn); {@code locationConsent} only counts with a version.
     */
    void recordConsent(UUID accountId, Integer consentVersion, boolean locationConsent, Instant now);

    /** Devices not revoked. */
    int liveDeviceCount(UUID accountId);

    /** Staff list; {@code associationId} null means all. Status reflects the live recycler status. */
    List<AccountSummary> list(UUID associationId);
}

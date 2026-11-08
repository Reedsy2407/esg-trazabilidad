package pe.esgtrazabilidad.auth.recycleraccount.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.DeviceSummary;
import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerDevice;

/** A phone's credential: its refresh token is stored only as a hash. Callers run in the service's transaction. */
public interface RecyclerDeviceStore {

    void insert(
            UUID deviceId,
            UUID accountId,
            String label,
            String installId,
            String refreshHash,
            Instant now,
            Instant absoluteExpiresAt);

    /** The device whose current or previous token has this hash, row locked so two refreshes are serialised. */
    Optional<RecyclerDevice> findByTokenHashForUpdate(String tokenHash);

    /** Replaces the current token: the old hash becomes the previous one, valid until {@code previousValidUntil}. */
    void rotate(UUID deviceId, String newHash, int newGeneration, String previousHash, Instant previousValidUntil, Instant now);

    void touch(UUID deviceId, Instant now);

    /** Irreversible. Keeps the first revocation time if it was already revoked. */
    void revoke(UUID deviceId, Instant now);

    /** What staff see of an account's phones, oldest first. Never a token or a hash. */
    List<DeviceSummary> listByAccount(UUID accountId);

    /**
     * Revokes the device if it belongs to this account (a device of another account is left alone).
     *
     * @return false if there is no such device in this account; true otherwise, also if it was already revoked
     */
    boolean revokeOwned(UUID accountId, UUID deviceId, Instant now);
}

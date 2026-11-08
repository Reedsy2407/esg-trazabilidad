package pe.esgtrazabilidad.auth.recycleraccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A phone's credential. {@code refreshHash} is the hash of the current refresh token, {@code previousHash}
 * of the one it replaced, which still works until {@code previousValidUntil}.
 */
public record RecyclerDevice(
        UUID id,
        UUID accountId,
        String refreshHash,
        int refreshGeneration,
        String previousHash,
        Instant previousValidUntil,
        Instant lastSeenAt,
        Instant absoluteExpiresAt,
        Instant revokedAt) {

    public boolean isRevoked() {
        return revokedAt != null;
    }
}

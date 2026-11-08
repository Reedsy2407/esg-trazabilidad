package pe.esgtrazabilidad.auth.recycleraccount.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code associationId} is the one the status copy had when the account was created, never the client's.
 * {@code consentVersion} is the version the person accepted (null if none) and {@code locationConsent}
 * whether they also accepted location.
 */
public record RecyclerAccount(
        UUID id,
        UUID recyclerId,
        UUID associationId,
        AccountStatus status,
        Instant createdAt,
        Instant activatedAt,
        Integer consentVersion,
        boolean locationConsent) {

    public static RecyclerAccount pending(UUID id, UUID recyclerId, UUID associationId, Instant now) {
        return new RecyclerAccount(id, recyclerId, associationId, AccountStatus.PENDING_ACTIVATION, now, null, null, false);
    }
}

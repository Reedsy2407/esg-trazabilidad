package pe.esgtrazabilidad.auth.recyclerstatus.domain;

import java.time.Instant;
import java.util.UUID;

/** What auth-service knows about a recycler: only what RecyclerStatusChanged told it (A.4). */
public record RecyclerStatusCopy(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant lastOccurredAt) {

    public boolean isActive() {
        return status == RecyclerStatus.ACTIVE;
    }
}

package pe.esgtrazabilidad.auth.recyclerstatus.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatusCopy;

/**
 * The local copy of recycler status (A.1, A.4). Written only by the RecyclerStatusChanged
 * consumer; read when an account is created and on every refresh.
 */
public interface RecyclerStatusCopyRepository {

    /**
     * Applies the event only if {@code occurredAt} is strictly newer than the stored one (or there
     * is no row yet), atomically. An older or repeated event changes nothing.
     *
     * @return true if the copy changed
     */
    boolean applyIfNewer(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt);

    /** Empty when no event for that recycler has arrived yet: callers treat that as not active. */
    Optional<RecyclerStatusCopy> find(UUID recyclerId);
}

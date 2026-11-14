package pe.esgtrazabilidad.collection.recycler.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;

/**
 * The local copy of recycler status (A.1, A.4). Written only by the
 * RecyclerStatusChanged consumer; read by field routes to check a recycler is
 * still active before accepting a write.
 */
public interface RecyclerStatusCopyRepository {

    /**
     * Applies the event only if {@code occurredAt} is strictly newer than the stored one
     * (or there is no row yet), atomically. An older or repeated event changes nothing.
     *
     * @return true if the copy changed
     */
    boolean applyIfNewer(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt);

    /** Fails closed: a recycler with no copy is not active. */
    boolean isActive(UUID recyclerId);

    /**
     * The association of an ACTIVE recycler, read from the copy; empty when there is no copy or
     * the recycler is INACTIVE (fails closed). Assignments take their association from here.
     */
    Optional<UUID> findActiveAssociationId(UUID recyclerId);
}

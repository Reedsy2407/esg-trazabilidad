package pe.esgtrazabilidad.kernel.events;

import java.util.List;
import java.util.UUID;

/**
 * Implemented once per service, against that service's own outbox table --
 * never a shared table (see SPEC-cross-service-events.md's Resolved
 * Decisions). {@link pe.esgtrazabilidad.kernel.amqp.OutboxDispatcher} is
 * generic and works against whichever implementation is present in a
 * given service's Spring context.
 */
public interface OutboxRepository {

    void save(OutboxEntry entry);

    List<OutboxEntry> findPendingBatch(int limit);

    void markProcessed(UUID id);

    void markFailed(UUID id);
}

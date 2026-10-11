package pe.esgtrazabilidad.collection.neighbor.port.in;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;
import pe.esgtrazabilidad.kernel.security.CurrentActor;

public interface ChangeNeighborStatusUseCase {

    /** ACTIVE -> INACTIVE, and pauses the neighbour's ACTIVE schedules in the same transaction (D8). */
    Neighbor deactivate(UUID id, String reason, CurrentActor actor);

    /** INACTIVE -> ACTIVE; paused schedules are left for staff to reactivate (D8). */
    Neighbor activate(UUID id, String reason, CurrentActor actor);

    /** The neighbour's deactivations and reactivations, newest first. */
    List<NeighborStatusChange> history(UUID id);
}

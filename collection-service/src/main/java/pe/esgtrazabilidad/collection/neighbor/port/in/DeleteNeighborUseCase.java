package pe.esgtrazabilidad.collection.neighbor.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.security.CurrentActor;

public interface DeleteNeighborUseCase {

    /**
     * Logical deletion, only for a neighbour without any collection record
     * (COL-011 otherwise: deactivate instead). Cancels its schedules. A
     * deleted neighbour answers COL-001 and leaves every list.
     */
    void delete(UUID id, String reason, CurrentActor actor);
}

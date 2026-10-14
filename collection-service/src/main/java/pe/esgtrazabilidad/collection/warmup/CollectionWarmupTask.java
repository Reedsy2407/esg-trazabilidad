package pe.esgtrazabilidad.collection.warmup;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.neighbor.port.in.ListNeighborsUseCase;
import pe.esgtrazabilidad.kernel.warmup.WarmupTask;

/** The neighbour list's first page, so Hibernate's first query and the mapping are done before a user asks. */
@Component
class CollectionWarmupTask implements WarmupTask {

    private final ListNeighborsUseCase neighbors;

    CollectionWarmupTask(ListNeighborsUseCase neighbors) {
        this.neighbors = neighbors;
    }

    @Override
    public String name() {
        return "neighbor-list";
    }

    @Override
    public void run() {
        neighbors.list(null, null, null, PageRequest.of(0, 1));
    }
}

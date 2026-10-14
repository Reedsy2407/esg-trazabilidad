package pe.esgtrazabilidad.recycler.warmup;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.kernel.warmup.WarmupTask;
import pe.esgtrazabilidad.recycler.association.port.in.ListAssociationsUseCase;

/** The association list's first page, so Hibernate's first query and the mapping are done before a user asks. */
@Component
class RecyclerWarmupTask implements WarmupTask {

    private final ListAssociationsUseCase associations;

    RecyclerWarmupTask(ListAssociationsUseCase associations) {
        this.associations = associations;
    }

    @Override
    public String name() {
        return "association-list";
    }

    @Override
    public void run() {
        associations.list(null, PageRequest.of(0, 1));
    }
}

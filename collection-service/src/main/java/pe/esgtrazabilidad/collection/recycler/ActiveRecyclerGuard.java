package pe.esgtrazabilidad.collection.recycler;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * Every write of the field app checks, live, that the recycler is still ACTIVE in the local copy
 * (A.1). A recycler with no copy counts as not active: it fails closed (COL-019, 403).
 */
@Component
public class ActiveRecyclerGuard {

    private final RecyclerStatusCopyRepository recyclers;

    ActiveRecyclerGuard(RecyclerStatusCopyRepository recyclers) {
        this.recyclers = recyclers;
    }

    public void require(UUID recyclerId) {
        if (!recyclers.isActive(recyclerId)) {
            throw new ApplicationException(CollectionErrors.RECYCLER_NOT_ACTIVE);
        }
    }
}

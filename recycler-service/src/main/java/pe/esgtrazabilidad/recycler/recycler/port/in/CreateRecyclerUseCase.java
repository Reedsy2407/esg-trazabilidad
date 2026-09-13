package pe.esgtrazabilidad.recycler.recycler.port.in;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;

public interface CreateRecyclerUseCase {

    Recycler create(CreateRecyclerCommand command);
}

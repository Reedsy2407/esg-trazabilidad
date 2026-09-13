package pe.esgtrazabilidad.recycler.recycler.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;

public interface GetRecyclerUseCase {

    Recycler getById(UUID id);
}

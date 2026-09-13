package pe.esgtrazabilidad.recycler.recycler.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;

public interface RecyclerRepository {

    Recycler save(Recycler recycler);

    Optional<Recycler> findById(UUID id);

    Optional<Recycler> findByDni(String dni);
}

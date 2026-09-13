package pe.esgtrazabilidad.recycler.recycler.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;

public interface RecyclerRepository {

    Recycler save(Recycler recycler);

    Recycler update(Recycler recycler);

    Optional<Recycler> findById(UUID id);

    Optional<Recycler> findByDni(String dni);

    Page<Recycler> findAll(UUID associationId, RecyclerStatus status, Pageable pageable);
}

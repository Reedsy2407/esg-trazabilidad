package pe.esgtrazabilidad.recycler.recycler.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;

public interface ListRecyclersUseCase {

    Page<Recycler> list(UUID associationId, RecyclerStatus status, Pageable pageable);
}

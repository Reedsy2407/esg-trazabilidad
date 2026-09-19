package pe.esgtrazabilidad.reporting.sigersolsync.port.in;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;

public interface ListSigersolSyncsUseCase {

    Page<SigersolSync> list(UUID associationId, Pageable pageable);
}

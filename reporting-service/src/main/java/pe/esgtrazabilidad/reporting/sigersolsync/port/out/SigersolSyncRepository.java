package pe.esgtrazabilidad.reporting.sigersolsync.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;

public interface SigersolSyncRepository {

    SigersolSync save(SigersolSync sigersolSync);

    Optional<SigersolSync> findById(UUID id);

    Page<SigersolSync> findAll(Pageable pageable);
}

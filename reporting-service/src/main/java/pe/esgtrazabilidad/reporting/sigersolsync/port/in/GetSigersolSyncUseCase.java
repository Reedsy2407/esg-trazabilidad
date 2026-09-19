package pe.esgtrazabilidad.reporting.sigersolsync.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;

public interface GetSigersolSyncUseCase {

    SigersolSync getById(UUID id);
}

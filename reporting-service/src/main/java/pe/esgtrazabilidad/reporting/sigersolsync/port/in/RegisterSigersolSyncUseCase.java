package pe.esgtrazabilidad.reporting.sigersolsync.port.in;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;

public interface RegisterSigersolSyncUseCase {

    SigersolSync register(RegisterSigersolSyncCommand command);
}

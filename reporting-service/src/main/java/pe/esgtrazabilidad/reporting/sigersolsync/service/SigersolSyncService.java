package pe.esgtrazabilidad.reporting.sigersolsync.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.GetSigersolSyncUseCase;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.ListSigersolSyncsUseCase;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.RegisterSigersolSyncCommand;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.RegisterSigersolSyncUseCase;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;

@Service
class SigersolSyncService implements RegisterSigersolSyncUseCase, GetSigersolSyncUseCase, ListSigersolSyncsUseCase {

    private final SigersolSyncRepository repository;

    SigersolSyncService(SigersolSyncRepository repository) {
        this.repository = repository;
    }

    @Override
    public SigersolSync register(RegisterSigersolSyncCommand command) {
        if (repository.existsOverlapping(command.associationId(), command.periodStart(), command.periodEnd())) {
            throw new ApplicationException(ReportingErrors.DUPLICATE_SIGERSOL_SYNC_PERIOD);
        }
        SigersolSync sigersolSync;
        try {
            sigersolSync = SigersolSync.create(
                    command.associationId(),
                    command.periodStart(),
                    command.periodEnd(),
                    command.hierarchyCompliancePercent(),
                    command.officialKilosDeclared(),
                    command.sourceNote());
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(ReportingErrors.INVALID_SIGERSOL_SYNC_DATA);
        }
        return repository.save(sigersolSync);
    }

    @Override
    public SigersolSync getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(ReportingErrors.SIGERSOL_SYNC_NOT_FOUND));
    }

    @Override
    public Page<SigersolSync> list(Pageable pageable) {
        return repository.findAll(pageable);
    }
}

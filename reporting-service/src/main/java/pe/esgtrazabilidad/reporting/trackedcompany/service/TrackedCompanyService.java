package pe.esgtrazabilidad.reporting.trackedcompany.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.GetTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ListTrackedCompaniesUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Service
class TrackedCompanyService
        implements RegisterTrackedCompanyUseCase, GetTrackedCompanyUseCase, ListTrackedCompaniesUseCase {

    private final TrackedCompanyRepository repository;

    TrackedCompanyService(TrackedCompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public TrackedCompany register(RegisterTrackedCompanyCommand command) {
        repository.findByRuc(command.ruc()).ifPresent(existing -> {
            throw new ApplicationException(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC);
        });
        TrackedCompany trackedCompany =
                TrackedCompany.create(command.name(), command.ruc(), command.associationId());
        return repository.save(trackedCompany);
    }

    @Override
    public TrackedCompany getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    @Override
    public Page<TrackedCompany> list(Pageable pageable) {
        return repository.findAll(pageable);
    }
}

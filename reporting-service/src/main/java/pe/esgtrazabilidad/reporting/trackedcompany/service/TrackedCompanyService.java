package pe.esgtrazabilidad.reporting.trackedcompany.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;
import pe.esgtrazabilidad.reporting.sunat.service.CachedRucLookup;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.GetTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ListTrackedCompaniesUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RefreshTrackedCompanySunatUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterUnverifiedTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterUnverifiedTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Service
class TrackedCompanyService
        implements RegisterTrackedCompanyUseCase,
                RegisterUnverifiedTrackedCompanyUseCase,
                RefreshTrackedCompanySunatUseCase,
                GetTrackedCompanyUseCase,
                ListTrackedCompaniesUseCase {

    private final TrackedCompanyRepository repository;
    private final CachedRucLookup rucLookup;
    private final Clock clock;

    @Autowired
    TrackedCompanyService(TrackedCompanyRepository repository, CachedRucLookup rucLookup) {
        this(repository, rucLookup, Clock.systemUTC());
    }

    TrackedCompanyService(TrackedCompanyRepository repository, CachedRucLookup rucLookup, Clock clock) {
        this.repository = repository;
        this.rucLookup = rucLookup;
        this.clock = clock;
    }

    /**
     * Order: RUC check digit (RPT-010) before any provider call, then the
     * duplicate check (RPT-002, no quota spent on a RUC we already hold), then
     * SUNAT. The legal name is whatever SUNAT answers.
     */
    @Override
    public TrackedCompany register(RegisterTrackedCompanyCommand command) {
        if (!Ruc.isValid(command.ruc())) {
            throw new ApplicationException(ReportingErrors.INVALID_RUC);
        }
        repository.findByRuc(command.ruc()).ifPresent(existing -> {
            throw new ApplicationException(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC);
        });
        RucLookupResult.Found found = requireVerifiable(rucLookup.lookup(Ruc.of(command.ruc())));
        TrackedCompany trackedCompany = TrackedCompany.registerVerified(
                found.legalName(),
                command.ruc(),
                command.associationId(),
                found.estado(),
                found.condicion(),
                clock.instant(),
                command.contact());
        return repository.save(trackedCompany);
    }

    /**
     * The fallback for when SUNAT does not answer. The server repeats the
     * lookup itself (no cache read: a day-old answer is not the truth now) and
     * refuses when SUNAT answers at all, so the client cannot skip the check.
     */
    @Override
    public TrackedCompany registerUnverified(RegisterUnverifiedTrackedCompanyCommand command) {
        if (!Ruc.isValid(command.ruc())) {
            throw new ApplicationException(ReportingErrors.INVALID_RUC);
        }
        repository.findByRuc(command.ruc()).ifPresent(existing -> {
            throw new ApplicationException(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC);
        });
        if (!(rucLookup.lookupFresh(Ruc.of(command.ruc())) instanceof RucLookupResult.Unavailable)) {
            throw new ApplicationException(ReportingErrors.SUNAT_ANSWERED);
        }
        return repository.save(TrackedCompany.registerUnverified(
                command.name().trim(), command.ruc(), command.associationId(), command.contact()));
    }

    /**
     * "Verificar con SUNAT". Always asks the provider. Any Found stores the
     * name, estado and condicion; only an unverified company in BAJA DEFINITIVA
     * is refused (it cannot become VERIFIED), and nothing changes when SUNAT
     * does not answer.
     */
    @Override
    public TrackedCompany refreshFromSunat(UUID id) {
        TrackedCompany company = getById(id);
        RucLookupResult.Found found = switch (rucLookup.lookupFresh(Ruc.of(company.getRuc()))) {
            case RucLookupResult.Unavailable unavailable ->
                    throw new ApplicationException(ReportingErrors.SUNAT_UNAVAILABLE);
            case RucLookupResult.NotFound notFound ->
                    throw new ApplicationException(ReportingErrors.RUC_NOT_FOUND_IN_SUNAT);
            case RucLookupResult.Found answer -> answer;
        };
        if (found.isDeregistered() && !company.isVerified()) {
            throw new ApplicationException(ReportingErrors.RUC_DEREGISTERED_IN_SUNAT);
        }
        if (!repository.updateSunatData(
                id, found.legalName(), true, found.estado(), found.condicion(), clock.instant())) {
            throw new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND);
        }
        return getById(id);
    }

    static RucLookupResult.Found requireVerifiable(RucLookupResult result) {
        return switch (result) {
            case RucLookupResult.Unavailable unavailable ->
                    throw new ApplicationException(ReportingErrors.SUNAT_UNAVAILABLE);
            case RucLookupResult.NotFound notFound ->
                    throw new ApplicationException(ReportingErrors.RUC_NOT_FOUND_IN_SUNAT);
            case RucLookupResult.Found found -> {
                if (found.isDeregistered()) {
                    throw new ApplicationException(ReportingErrors.RUC_DEREGISTERED_IN_SUNAT);
                }
                yield found;
            }
        };
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

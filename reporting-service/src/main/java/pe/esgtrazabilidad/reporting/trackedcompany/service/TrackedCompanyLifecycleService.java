package pe.esgtrazabilidad.reporting.trackedcompany.service;

import java.time.Clock;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;
import pe.esgtrazabilidad.reporting.sunat.service.CachedRucLookup;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.EditTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ManageTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

/**
 * The company's life after registering it. Every change is one conditional
 * write in the repository; the reads here are only the fast path that picks the
 * right error, and a lost race falls back to the same error.
 */
@Service
class TrackedCompanyLifecycleService implements ManageTrackedCompanyUseCase {

    private final TrackedCompanyRepository repository;
    private final CachedRucLookup rucLookup;
    private final Clock clock;

    @Autowired
    TrackedCompanyLifecycleService(TrackedCompanyRepository repository, CachedRucLookup rucLookup) {
        this(repository, rucLookup, Clock.systemUTC());
    }

    TrackedCompanyLifecycleService(TrackedCompanyRepository repository, CachedRucLookup rucLookup, Clock clock) {
        this.repository = repository;
        this.rucLookup = rucLookup;
        this.clock = clock;
    }

    /**
     * Contact always. RUC and association only while the company has no
     * certificate (annulled ones count), checked before spending a SUNAT call;
     * a new RUC is looked up like a registration and takes SUNAT's name.
     */
    @Override
    @Transactional
    public TrackedCompany edit(EditTrackedCompanyCommand command) {
        TrackedCompany company = require(command.id());
        boolean rucChanges = command.ruc() != null && !command.ruc().equals(company.getRuc());
        boolean associationChanges =
                command.associationId() != null && !command.associationId().equals(company.getAssociationId());

        if (rucChanges || associationChanges) {
            if (rucChanges && !Ruc.isValid(command.ruc())) {
                throw new ApplicationException(ReportingErrors.INVALID_RUC);
            }
            if (repository.hasCertificates(company.getId())) {
                throw new ApplicationException(ReportingErrors.TRACKED_COMPANY_HAS_CERTIFICATES);
            }
            changeIdentity(company, command, rucChanges, associationChanges);
        }
        if (command.contactName() != null || command.contactEmail() != null || command.contactPhone() != null) {
            CompanyContact current = company.getContact();
            repository.updateContact(
                    company.getId(),
                    new CompanyContact(
                            merge(current.name(), command.contactName()),
                            merge(current.email(), command.contactEmail()),
                            merge(current.phone(), command.contactPhone())));
        }
        return require(company.getId());
    }

    private void changeIdentity(
            TrackedCompany company, EditTrackedCompanyCommand command, boolean rucChanges, boolean associationChanges) {
        String ruc = company.getRuc();
        String name = company.getName();
        boolean verified = company.isVerified();
        String estado = company.getSunatEstado();
        String condicion = company.getSunatCondicion();
        java.time.Instant checkedAt = company.getSunatCheckedAt();
        if (rucChanges) {
            repository.findByRuc(command.ruc()).ifPresent(existing -> {
                throw new ApplicationException(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC);
            });
            RucLookupResult.Found found =
                    TrackedCompanyService.requireVerifiable(rucLookup.lookup(Ruc.of(command.ruc())));
            ruc = command.ruc();
            name = found.legalName();
            verified = true;
            estado = found.estado();
            condicion = found.condicion();
            checkedAt = clock.instant();
        }
        UUID associationId = associationChanges ? command.associationId() : company.getAssociationId();
        if (!repository.changeIdentityIfNoCertificates(
                company.getId(), ruc, associationId, name, verified, estado, condicion, checkedAt)) {
            // A certificate was issued between the check and the write, or the company vanished.
            require(company.getId());
            throw new ApplicationException(ReportingErrors.TRACKED_COMPANY_HAS_CERTIFICATES);
        }
    }

    @Override
    @Transactional
    public TrackedCompany deactivate(UUID id, String reason, CurrentActor actor) {
        return changeStatus(id, TrackedCompanyStatus.ACTIVE, TrackedCompanyStatus.INACTIVE, reason, actor);
    }

    @Override
    @Transactional
    public TrackedCompany activate(UUID id, String reason, CurrentActor actor) {
        return changeStatus(id, TrackedCompanyStatus.INACTIVE, TrackedCompanyStatus.ACTIVE, reason, actor);
    }

    private TrackedCompany changeStatus(
            UUID id, TrackedCompanyStatus from, TrackedCompanyStatus to, String reason, CurrentActor actor) {
        TrackedCompany company = require(id);
        if (company.getStatus() == to
                || !repository.changeStatus(id, from, to, reason.strip(), actor, clock.instant())) {
            // The conditional UPDATE lost a race: whoever won already moved it (or deleted it).
            require(id);
            throw new ApplicationException(ReportingErrors.TRACKED_COMPANY_ALREADY_IN_STATUS);
        }
        return require(id);
    }

    @Override
    @Transactional
    public void delete(UUID id, String reason, CurrentActor actor) {
        require(id);
        if (repository.hasCertificates(id)
                || !repository.deleteIfNoCertificates(id, reason.strip(), actor, clock.instant())) {
            require(id);
            throw new ApplicationException(ReportingErrors.TRACKED_COMPANY_HAS_CERTIFICATES);
        }
    }

    private TrackedCompany require(UUID id) {
        return repository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    /** null keeps the stored value, a blank clears it. */
    private static String merge(String current, String requested) {
        if (requested == null) {
            return current;
        }
        return requested.isBlank() ? null : requested.trim();
    }
}

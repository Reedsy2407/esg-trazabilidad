package pe.esgtrazabilidad.reporting.trackedcompany.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;

/**
 * Reads leave out logically deleted companies. Every method that returns a
 * boolean is one conditional write: false means the row did not match (it does
 * not exist, is deleted, or the extra condition failed), so the caller decides
 * which error that is.
 */
public interface TrackedCompanyRepository {

    TrackedCompany save(TrackedCompany trackedCompany);

    /**
     * Stores what a successful SUNAT lookup answered and marks the company
     * VERIFIED when {@code verified}.
     */
    boolean updateSunatData(
            UUID id, String name, boolean verified, String estado, String condicion, Instant checkedAt);

    boolean updateContact(UUID id, CompanyContact contact);

    boolean hasCertificates(UUID id);

    /** RUC and association, only while the company has no certificate at all (annulled ones count). */
    boolean changeIdentityIfNoCertificates(
            UUID id,
            String ruc,
            UUID associationId,
            String name,
            boolean verified,
            String estado,
            String condicion,
            Instant checkedAt);

    /** Moves the status and writes the history row together; false when the status is no longer {@code from}. */
    boolean changeStatus(
            UUID id, TrackedCompanyStatus from, TrackedCompanyStatus to, String reason, CurrentActor actor, Instant at);

    /** Logical deletion, only without any certificate. */
    boolean deleteIfNoCertificates(UUID id, String reason, CurrentActor actor, Instant at);

    Optional<TrackedCompany> findById(UUID id);

    Optional<TrackedCompany> findByRuc(String ruc);

    Page<TrackedCompany> findAll(Pageable pageable);
}

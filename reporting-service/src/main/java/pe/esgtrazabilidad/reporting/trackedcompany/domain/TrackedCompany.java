package pe.esgtrazabilidad.reporting.trackedcompany.domain;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * reporting-service's own, minimal record of the Company <-> Association
 * link nothing else in the system has (see SPEC-reporting-service.md's
 * Objective/Resolved Decisions): collection-service's Company has zero
 * relationship to recycler-service's Association, by that module's own
 * explicit design. Since v3 the legal name, SUNAT's estado and condicion come
 * from a SUNAT lookup by RUC (VERIFIED); a company registered while SUNAT did
 * not answer keeps a hand-typed name and stays UNVERIFIED until a lookup
 * succeeds.
 */
public class TrackedCompany {

    private final UUID id;
    private final String name;
    private final String ruc;
    private final UUID associationId;
    private final TrackedCompanyStatus status;
    private final TrackedCompanyVerification verification;
    private final String sunatEstado;
    private final String sunatCondicion;
    private final Instant sunatCheckedAt;
    private final CompanyContact contact;

    private TrackedCompany(
            UUID id,
            String name,
            String ruc,
            UUID associationId,
            TrackedCompanyStatus status,
            TrackedCompanyVerification verification,
            String sunatEstado,
            String sunatCondicion,
            Instant sunatCheckedAt,
            CompanyContact contact) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.associationId = associationId;
        this.status = status;
        this.verification = verification;
        this.sunatEstado = sunatEstado;
        this.sunatCondicion = sunatCondicion;
        this.sunatCheckedAt = sunatCheckedAt;
        this.contact = contact == null ? CompanyContact.NONE : contact;
    }

    /** A verified company without SUNAT detail: what a lookup-less caller (and most unit tests) needs. */
    public static TrackedCompany create(String name, String ruc, UUID associationId) {
        return create(name, ruc, associationId, TrackedCompanyVerification.VERIFIED, null, null, null, null);
    }

    /** A company whose name, estado and condicion come from a successful SUNAT lookup. */
    public static TrackedCompany registerVerified(
            String legalName,
            String ruc,
            UUID associationId,
            String sunatEstado,
            String sunatCondicion,
            Instant checkedAt,
            CompanyContact contact) {
        return create(
                legalName,
                ruc,
                associationId,
                TrackedCompanyVerification.VERIFIED,
                sunatEstado,
                sunatCondicion,
                checkedAt,
                contact);
    }

    /** A company registered while SUNAT did not answer: the name is the operator's, until a refresh succeeds. */
    public static TrackedCompany registerUnverified(
            String name, String ruc, UUID associationId, CompanyContact contact) {
        return create(name, ruc, associationId, TrackedCompanyVerification.UNVERIFIED, null, null, null, contact);
    }

    public boolean isVerified() {
        return verification == TrackedCompanyVerification.VERIFIED;
    }

    private static TrackedCompany create(
            String name,
            String ruc,
            UUID associationId,
            TrackedCompanyVerification verification,
            String sunatEstado,
            String sunatCondicion,
            Instant checkedAt,
            CompanyContact contact) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos numéricos");
        }
        if (associationId == null) {
            throw new IllegalArgumentException("La asociación rastreada es obligatoria");
        }
        return new TrackedCompany(
                IdGenerator.generate(),
                name,
                ruc,
                associationId,
                TrackedCompanyStatus.ACTIVE,
                verification,
                sunatEstado,
                sunatCondicion,
                checkedAt,
                contact);
    }

    public static TrackedCompany reconstruct(
            UUID id, String name, String ruc, UUID associationId, TrackedCompanyStatus status) {
        return new TrackedCompany(
                id, name, ruc, associationId, status, TrackedCompanyVerification.VERIFIED, null, null, null, null);
    }

    public static TrackedCompany reconstruct(
            UUID id,
            String name,
            String ruc,
            UUID associationId,
            TrackedCompanyStatus status,
            TrackedCompanyVerification verification,
            String sunatEstado,
            String sunatCondicion,
            Instant sunatCheckedAt,
            CompanyContact contact) {
        return new TrackedCompany(
                id, name, ruc, associationId, status, verification, sunatEstado, sunatCondicion, sunatCheckedAt, contact);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRuc() {
        return ruc;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public TrackedCompanyStatus getStatus() {
        return status;
    }

    public TrackedCompanyVerification getVerification() {
        return verification;
    }

    public String getSunatEstado() {
        return sunatEstado;
    }

    public String getSunatCondicion() {
        return sunatCondicion;
    }

    public Instant getSunatCheckedAt() {
        return sunatCheckedAt;
    }

    public CompanyContact getContact() {
        return contact;
    }

    /** SUNAT reports the taxpayer as not locatable at its registered address. */
    public boolean isNotLocated() {
        return sunatCondicion != null && sunatCondicion.trim().equalsIgnoreCase("NO HABIDO");
    }
}

package pe.esgtrazabilidad.reporting.trackedcompany.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * reporting-service's own, minimal record of the Company <-> Association
 * link nothing else in the system has (see SPEC-reporting-service.md's
 * Objective/Resolved Decisions): collection-service's Company has zero
 * relationship to recycler-service's Association, by that module's own
 * explicit design. name/ruc are re-entered here rather than looked up
 * live from collection-service (no synchronous cross-service call, ever)
 * -- the same eventual-consistency trade-off already established for
 * associationId itself.
 */
public class TrackedCompany {

    private final UUID id;
    private final String name;
    private final String ruc;
    private final UUID associationId;
    private final TrackedCompanyStatus status;

    private TrackedCompany(
            UUID id, String name, String ruc, UUID associationId, TrackedCompanyStatus status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.associationId = associationId;
        this.status = status;
    }

    public static TrackedCompany create(String name, String ruc, UUID associationId) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos numéricos");
        }
        if (associationId == null) {
            throw new IllegalArgumentException("La asociación rastreada es obligatoria");
        }
        return new TrackedCompany(IdGenerator.generate(), name, ruc, associationId, TrackedCompanyStatus.ACTIVE);
    }

    public static TrackedCompany reconstruct(
            UUID id, String name, String ruc, UUID associationId, TrackedCompanyStatus status) {
        return new TrackedCompany(id, name, ruc, associationId, status);
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
}

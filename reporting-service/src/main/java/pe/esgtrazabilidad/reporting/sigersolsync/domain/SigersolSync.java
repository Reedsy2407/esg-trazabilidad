package pe.esgtrazabilidad.reporting.sigersolsync.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * A manually-entered official MINAM SIGERSOL declaration snapshot for one
 * association's period -- see SPEC-reporting-service.md's Resolved
 * Decisions for why this is manual data entry, not a live API integration
 * (no public SIGERSOL API exists to build one against). Immutable once
 * created: no update/delete, same "historical fact" discipline already
 * applied elsewhere in this codebase.
 */
public class SigersolSync {

    private final UUID id;
    private final UUID associationId;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal hierarchyCompliancePercent;
    private final BigDecimal officialKilosDeclared;
    private final Instant declaredAt;
    private final String sourceNote;
    private final SigersolOrigin origin;
    private final UUID registeredById;
    private final String registeredByEmail;
    private final String reference;

    private SigersolSync(
            UUID id,
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            Instant declaredAt,
            String sourceNote,
            SigersolOrigin origin,
            UUID registeredById,
            String registeredByEmail,
            String reference) {
        this.id = id;
        this.associationId = associationId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.hierarchyCompliancePercent = hierarchyCompliancePercent;
        this.officialKilosDeclared = officialKilosDeclared;
        this.declaredAt = declaredAt;
        this.sourceNote = sourceNote;
        this.origin = origin;
        this.registeredById = registeredById;
        this.registeredByEmail = registeredByEmail;
        this.reference = reference;
    }

    /** A record without origin or author: what a pre-v3 row looks like ("origen no registrado"). */
    public static SigersolSync create(
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            String sourceNote) {
        return create(
                associationId,
                periodStart,
                periodEnd,
                hierarchyCompliancePercent,
                officialKilosDeclared,
                sourceNote,
                null,
                null,
                null,
                null);
    }

    /**
     * A new record (D1): origin and author are required, together. Who comes from the token, never
     * from the body; the reference is the operator's optional pointer to their proof.
     */
    public static SigersolSync create(
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            String sourceNote,
            SigersolOrigin origin,
            UUID registeredById,
            String registeredByEmail,
            String reference) {
        if ((origin == null) != (registeredById == null)) {
            throw new IllegalArgumentException("El origen y quién registró van juntos");
        }
        if (reference != null && reference.length() > 255) {
            throw new IllegalArgumentException("La referencia no puede pasar de 255 caracteres");
        }
        if (associationId == null) {
            throw new IllegalArgumentException("La asociación es obligatoria");
        }
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("El periodo declarado no es válido");
        }
        if (hierarchyCompliancePercent == null
                || hierarchyCompliancePercent.compareTo(BigDecimal.ZERO) < 0
                || hierarchyCompliancePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("El porcentaje de cumplimiento debe estar entre 0 y 100");
        }
        if (officialKilosDeclared != null && officialKilosDeclared.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Los kilos oficiales declarados no pueden ser negativos");
        }
        return new SigersolSync(
                IdGenerator.generate(),
                associationId,
                periodStart,
                periodEnd,
                hierarchyCompliancePercent,
                officialKilosDeclared,
                Instant.now(),
                sourceNote,
                origin,
                registeredById,
                registeredByEmail,
                reference == null || reference.isBlank() ? null : reference.trim());
    }

    public static SigersolSync reconstruct(
            UUID id,
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            Instant declaredAt,
            String sourceNote) {
        return reconstruct(
                id,
                associationId,
                periodStart,
                periodEnd,
                hierarchyCompliancePercent,
                officialKilosDeclared,
                declaredAt,
                sourceNote,
                null,
                null,
                null,
                null);
    }

    public static SigersolSync reconstruct(
            UUID id,
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            Instant declaredAt,
            String sourceNote,
            SigersolOrigin origin,
            UUID registeredById,
            String registeredByEmail,
            String reference) {
        return new SigersolSync(
                id,
                associationId,
                periodStart,
                periodEnd,
                hierarchyCompliancePercent,
                officialKilosDeclared,
                declaredAt,
                sourceNote,
                origin,
                registeredById,
                registeredByEmail,
                reference);
    }

    /** Null for records made before v3: shown as "origen no registrado". */
    public SigersolOrigin getOrigin() {
        return origin;
    }

    public UUID getRegisteredById() {
        return registeredById;
    }

    public String getRegisteredByEmail() {
        return registeredByEmail;
    }

    public String getReference() {
        return reference;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getHierarchyCompliancePercent() {
        return hierarchyCompliancePercent;
    }

    public BigDecimal getOfficialKilosDeclared() {
        return officialKilosDeclared;
    }

    public Instant getDeclaredAt() {
        return declaredAt;
    }

    public String getSourceNote() {
        return sourceNote;
    }
}

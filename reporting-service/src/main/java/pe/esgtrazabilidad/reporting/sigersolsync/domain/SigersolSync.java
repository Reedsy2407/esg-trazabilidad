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

    private SigersolSync(
            UUID id,
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            Instant declaredAt,
            String sourceNote) {
        this.id = id;
        this.associationId = associationId;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.hierarchyCompliancePercent = hierarchyCompliancePercent;
        this.officialKilosDeclared = officialKilosDeclared;
        this.declaredAt = declaredAt;
        this.sourceNote = sourceNote;
    }

    public static SigersolSync create(
            UUID associationId,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal hierarchyCompliancePercent,
            BigDecimal officialKilosDeclared,
            String sourceNote) {
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
                sourceNote);
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
        return new SigersolSync(
                id,
                associationId,
                periodStart,
                periodEnd,
                hierarchyCompliancePercent,
                officialKilosDeclared,
                declaredAt,
                sourceNote);
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

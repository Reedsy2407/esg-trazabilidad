package pe.esgtrazabilidad.reporting.sigersolsync.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "sigersol_sync")
class SigersolSyncEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "hierarchy_compliance_percent", nullable = false)
    private BigDecimal hierarchyCompliancePercent;

    @Column(name = "official_kilos_declared")
    private BigDecimal officialKilosDeclared;

    @Column(name = "declared_at", nullable = false)
    private Instant declaredAt;

    @Column(name = "source_note")
    private String sourceNote;

    @Transient
    private boolean isNew = false;

    protected SigersolSyncEntity() {
    }

    SigersolSyncEntity(
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
        this.isNew = true;
    }

    @PostLoad
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    UUID getAssociationId() {
        return associationId;
    }

    LocalDate getPeriodStart() {
        return periodStart;
    }

    LocalDate getPeriodEnd() {
        return periodEnd;
    }

    BigDecimal getHierarchyCompliancePercent() {
        return hierarchyCompliancePercent;
    }

    BigDecimal getOfficialKilosDeclared() {
        return officialKilosDeclared;
    }

    Instant getDeclaredAt() {
        return declaredAt;
    }

    String getSourceNote() {
        return sourceNote;
    }
}

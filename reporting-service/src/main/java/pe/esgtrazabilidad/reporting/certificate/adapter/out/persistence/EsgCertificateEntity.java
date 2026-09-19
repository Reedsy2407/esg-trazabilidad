package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

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
@Table(name = "esg_certificate")
class EsgCertificateEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "tracked_company_id", nullable = false)
    private UUID trackedCompanyId;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Column(name = "company_ruc", nullable = false, length = 11)
    private String companyRuc;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "kilos_trazados", nullable = false)
    private BigDecimal kilosTrazados;

    @Column(name = "hierarchy_compliance_percent", nullable = false)
    private BigDecimal hierarchyCompliancePercent;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Transient
    private boolean isNew = false;

    protected EsgCertificateEntity() {
    }

    EsgCertificateEntity(
            UUID id,
            UUID trackedCompanyId,
            UUID associationId,
            String companyName,
            String companyRuc,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal kilosTrazados,
            BigDecimal hierarchyCompliancePercent,
            Instant issuedAt) {
        this.id = id;
        this.trackedCompanyId = trackedCompanyId;
        this.associationId = associationId;
        this.companyName = companyName;
        this.companyRuc = companyRuc;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.kilosTrazados = kilosTrazados;
        this.hierarchyCompliancePercent = hierarchyCompliancePercent;
        this.issuedAt = issuedAt;
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

    UUID getTrackedCompanyId() {
        return trackedCompanyId;
    }

    UUID getAssociationId() {
        return associationId;
    }

    String getCompanyName() {
        return companyName;
    }

    String getCompanyRuc() {
        return companyRuc;
    }

    LocalDate getPeriodStart() {
        return periodStart;
    }

    LocalDate getPeriodEnd() {
        return periodEnd;
    }

    BigDecimal getKilosTrazados() {
        return kilosTrazados;
    }

    BigDecimal getHierarchyCompliancePercent() {
        return hierarchyCompliancePercent;
    }

    Instant getIssuedAt() {
        return issuedAt;
    }
}

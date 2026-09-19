package pe.esgtrazabilidad.reporting.certificate.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

/**
 * An immutable historical fact once issued -- same discipline already
 * applied to CollectionRecord (create + get + list only, no update/delete
 * endpoint). Every value it holds is a COPY frozen at issuance time
 * (company name/RUC, associationId, kilos, compliance %), never a live
 * foreign-key join re-evaluated later -- see SPEC-reporting-service.md's
 * Resolved Decisions. This is what makes "a certificate never silently
 * changes after issuance, even if backdated events arrive later" true, not
 * just intended.
 */
public class EsgCertificate {

    private final UUID id;
    private final UUID trackedCompanyId;
    private final UUID associationId;
    private final String companyName;
    private final String companyRuc;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final BigDecimal kilosTrazados;
    private final BigDecimal hierarchyCompliancePercent;
    private final Instant issuedAt;

    private EsgCertificate(
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
    }

    public static EsgCertificate issue(
            TrackedCompany trackedCompany,
            LocalDate periodStart,
            LocalDate periodEnd,
            BigDecimal kilosTrazados,
            BigDecimal hierarchyCompliancePercent) {
        if (periodStart == null || periodEnd == null || periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("El periodo del certificado no es válido");
        }
        return new EsgCertificate(
                IdGenerator.generate(),
                trackedCompany.getId(),
                trackedCompany.getAssociationId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                periodStart,
                periodEnd,
                kilosTrazados,
                hierarchyCompliancePercent,
                Instant.now());
    }

    public static EsgCertificate reconstruct(
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
        return new EsgCertificate(
                id,
                trackedCompanyId,
                associationId,
                companyName,
                companyRuc,
                periodStart,
                periodEnd,
                kilosTrazados,
                hierarchyCompliancePercent,
                issuedAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getTrackedCompanyId() {
        return trackedCompanyId;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCompanyRuc() {
        return companyRuc;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public BigDecimal getKilosTrazados() {
        return kilosTrazados;
    }

    public BigDecimal getHierarchyCompliancePercent() {
        return hierarchyCompliancePercent;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }
}

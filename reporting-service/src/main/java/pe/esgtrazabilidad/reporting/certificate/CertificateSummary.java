package pe.esgtrazabilidad.reporting.certificate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A computed value object, NOT a persisted JPA entity -- see
 * SPEC-reporting-service.md's Resolved Decisions. Always computed live from
 * TracedCollectionEntryJpaRepository + SigersolSyncRepository; issuing a
 * certificate (Task 52) is the only action that freezes these same numbers
 * into durable state. hierarchyCompliancePercent is nullable here (unlike
 * on an issued EsgCertificate): a preview is allowed even before any
 * SigersolSync record exists for the period -- RPT-005 only blocks actual
 * issuance, not this preview.
 */
public record CertificateSummary(
        UUID trackedCompanyId,
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal kilosTrazados,
        BigDecimal hierarchyCompliancePercent) {
}

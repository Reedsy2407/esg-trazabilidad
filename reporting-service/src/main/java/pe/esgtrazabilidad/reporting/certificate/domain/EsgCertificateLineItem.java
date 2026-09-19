package pe.esgtrazabilidad.reporting.certificate.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * A frozen copy of one TracedCollectionEntry row that contributed to an
 * issued EsgCertificate's kilosTrazados total -- snapshotted at issuance
 * time rather than re-queried by date range on every export, so the
 * certificate's exported detail never drifts even if the live ledger
 * keeps growing (see SPEC-reporting-service.md's Resolved Decisions).
 */
public class EsgCertificateLineItem {

    private final UUID id;
    private final UUID certificateId;
    private final LocalDate collectionDate;
    private final BigDecimal weightKg;

    private EsgCertificateLineItem(UUID id, UUID certificateId, LocalDate collectionDate, BigDecimal weightKg) {
        this.id = id;
        this.certificateId = certificateId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
    }

    public static EsgCertificateLineItem of(UUID certificateId, LocalDate collectionDate, BigDecimal weightKg) {
        return new EsgCertificateLineItem(IdGenerator.generate(), certificateId, collectionDate, weightKg);
    }

    public static EsgCertificateLineItem reconstruct(
            UUID id, UUID certificateId, LocalDate collectionDate, BigDecimal weightKg) {
        return new EsgCertificateLineItem(id, certificateId, collectionDate, weightKg);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCertificateId() {
        return certificateId;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }
}

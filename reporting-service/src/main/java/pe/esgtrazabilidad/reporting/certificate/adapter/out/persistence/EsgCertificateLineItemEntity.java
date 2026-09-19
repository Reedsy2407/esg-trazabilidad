package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.math.BigDecimal;
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
@Table(name = "esg_certificate_line_item")
class EsgCertificateLineItemEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "certificate_id", nullable = false)
    private UUID certificateId;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Transient
    private boolean isNew = false;

    protected EsgCertificateLineItemEntity() {
    }

    EsgCertificateLineItemEntity(UUID id, UUID certificateId, LocalDate collectionDate, BigDecimal weightKg) {
        this.id = id;
        this.certificateId = certificateId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
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

    UUID getCertificateId() {
        return certificateId;
    }

    LocalDate getCollectionDate() {
        return collectionDate;
    }

    BigDecimal getWeightKg() {
        return weightKg;
    }
}

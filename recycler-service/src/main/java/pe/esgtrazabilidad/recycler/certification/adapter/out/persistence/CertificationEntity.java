package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

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
@Table(name = "certification")
class CertificationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "certification_type")
    private String certificationType;

    @Column(name = "issued_date")
    private LocalDate issuedDate;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    @Transient
    private boolean isNew = false;

    protected CertificationEntity() {
    }

    CertificationEntity(
            UUID id, UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        this.id = id;
        this.associationId = associationId;
        this.certificationType = certificationType;
        this.issuedDate = issuedDate;
        this.expirationDate = expirationDate;
        this.isNew = true;
    }

    /**
     * For updating a row that's already persisted. Unlike the public
     * constructor (always isNew=true, correct for create()), this produces
     * an entity Spring Data routes through merge() instead of persist().
     */
    static CertificationEntity existing(
            UUID id, UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        CertificationEntity entity = new CertificationEntity();
        entity.id = id;
        entity.associationId = associationId;
        entity.certificationType = certificationType;
        entity.issuedDate = issuedDate;
        entity.expirationDate = expirationDate;
        entity.isNew = false;
        return entity;
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

    String getCertificationType() {
        return certificationType;
    }

    LocalDate getIssuedDate() {
        return issuedDate;
    }

    LocalDate getExpirationDate() {
        return expirationDate;
    }
}

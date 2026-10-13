package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "tracked_company")
class TrackedCompanyEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 11)
    private String ruc;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String verification;

    @Column(name = "sunat_estado")
    private String sunatEstado;

    @Column(name = "sunat_condicion")
    private String sunatCondicion;

    @Column(name = "sunat_checked_at")
    private Instant sunatCheckedAt;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    /** Logical deletion: the four columns are set together by one conditional UPDATE, never by this class. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_id")
    private UUID deletedById;

    @Column(name = "deleted_by_email")
    private String deletedByEmail;

    @Column(name = "deletion_reason")
    private String deletionReason;

    @Transient
    private boolean isNew = false;

    protected TrackedCompanyEntity() {
    }

    TrackedCompanyEntity(
            UUID id,
            String name,
            String ruc,
            UUID associationId,
            String status,
            String verification,
            String sunatEstado,
            String sunatCondicion,
            Instant sunatCheckedAt,
            String contactName,
            String contactEmail,
            String contactPhone) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.associationId = associationId;
        this.status = status;
        this.verification = verification;
        this.sunatEstado = sunatEstado;
        this.sunatCondicion = sunatCondicion;
        this.sunatCheckedAt = sunatCheckedAt;
        this.contactName = contactName;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
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

    String getName() {
        return name;
    }

    String getRuc() {
        return ruc;
    }

    UUID getAssociationId() {
        return associationId;
    }

    String getStatus() {
        return status;
    }

    String getVerification() {
        return verification;
    }

    String getSunatEstado() {
        return sunatEstado;
    }

    String getSunatCondicion() {
        return sunatCondicion;
    }

    Instant getSunatCheckedAt() {
        return sunatCheckedAt;
    }

    String getContactName() {
        return contactName;
    }

    String getContactEmail() {
        return contactEmail;
    }

    String getContactPhone() {
        return contactPhone;
    }
}

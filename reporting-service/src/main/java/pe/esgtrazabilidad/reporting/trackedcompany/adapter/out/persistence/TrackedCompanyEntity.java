package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

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

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected TrackedCompanyEntity() {
    }

    TrackedCompanyEntity(UUID id, String name, String ruc, UUID associationId, String status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.associationId = associationId;
        this.status = status;
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
}

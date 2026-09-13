package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "recycler")
class RecyclerEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true, length = 8)
    private String dni;

    private String phone;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected RecyclerEntity() {
    }

    RecyclerEntity(UUID id, String fullName, String dni, String phone, UUID associationId, String status) {
        this.id = id;
        this.fullName = fullName;
        this.dni = dni;
        this.phone = phone;
        this.associationId = associationId;
        this.status = status;
        this.isNew = true;
    }

    /**
     * For updating a row that's already persisted. Unlike the public
     * constructor (always isNew=true, correct for create()), this produces
     * an entity Spring Data routes through merge() instead of persist().
     */
    static RecyclerEntity existing(UUID id, String fullName, String dni, String phone, UUID associationId, String status) {
        RecyclerEntity entity = new RecyclerEntity();
        entity.id = id;
        entity.fullName = fullName;
        entity.dni = dni;
        entity.phone = phone;
        entity.associationId = associationId;
        entity.status = status;
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

    String getFullName() {
        return fullName;
    }

    String getDni() {
        return dni;
    }

    String getPhone() {
        return phone;
    }

    UUID getAssociationId() {
        return associationId;
    }

    String getStatus() {
        return status;
    }
}

package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "association")
class AssociationEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "registration_number")
    private String registrationNumber;

    private String address;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected AssociationEntity() {
    }

    AssociationEntity(
            UUID id,
            String name,
            String ruc,
            String registrationNumber,
            String address,
            String contactEmail,
            String contactPhone,
            String status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.registrationNumber = registrationNumber;
        this.address = address;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
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

    String getRegistrationNumber() {
        return registrationNumber;
    }

    String getAddress() {
        return address;
    }

    String getContactEmail() {
        return contactEmail;
    }

    String getContactPhone() {
        return contactPhone;
    }

    String getStatus() {
        return status;
    }
}

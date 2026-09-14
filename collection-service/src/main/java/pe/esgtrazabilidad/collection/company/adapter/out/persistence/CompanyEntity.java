package pe.esgtrazabilidad.collection.company.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "company")
class CompanyEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true, length = 11)
    private String ruc;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    private String address;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected CompanyEntity() {
    }

    CompanyEntity(
            UUID id, String name, String ruc, String contactEmail, String contactPhone, String address, String status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
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

    String getContactEmail() {
        return contactEmail;
    }

    String getContactPhone() {
        return contactPhone;
    }

    String getAddress() {
        return address;
    }

    String getStatus() {
        return status;
    }
}

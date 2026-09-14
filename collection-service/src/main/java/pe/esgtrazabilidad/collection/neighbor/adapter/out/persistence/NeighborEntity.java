package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "neighbor")
class NeighborEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    private String address;

    private String district;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected NeighborEntity() {
    }

    NeighborEntity(UUID id, String fullName, String phone, String address, String district, String status) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.district = district;
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

    String getFullName() {
        return fullName;
    }

    String getPhone() {
        return phone;
    }

    String getAddress() {
        return address;
    }

    String getDistrict() {
        return district;
    }

    String getStatus() {
        return status;
    }
}

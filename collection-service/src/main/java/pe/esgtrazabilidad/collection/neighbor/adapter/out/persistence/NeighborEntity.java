package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

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
@Table(name = "neighbor")
class NeighborEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    private String phone;

    private String address;

    private String district;

    @Column(name = "district_ubigeo")
    private String districtUbigeo;

    // Written on insert only: after that, only the compare-and-set in
    // NeighborJpaRepository.updateStatusIfCurrent changes it (a JPQL bulk
    // update ignores updatable=false). An edit's full-row merge must never
    // write back a status it read before a concurrent transition.
    @Column(nullable = false, updatable = false)
    private String status;

    // Deletion columns: never written by insert or merge, only by the
    // conditional bulk update in NeighborJpaRepository.markDeleted.
    @Column(name = "deleted_at", insertable = false, updatable = false)
    private Instant deletedAt;

    @Transient
    private boolean isNew = false;

    protected NeighborEntity() {
    }

    NeighborEntity(
            UUID id, String fullName, String phone, String address, String district, String districtUbigeo, String status) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.district = district;
        this.districtUbigeo = districtUbigeo;
        this.status = status;
        this.isNew = true;
    }

    /**
     * Same fields as the constructor but isNew=false, so save() merges
     * instead of inserting -- see [[persistable_update_path]].
     */
    static NeighborEntity existing(
            UUID id, String fullName, String phone, String address, String district, String districtUbigeo, String status) {
        NeighborEntity entity = new NeighborEntity(id, fullName, phone, address, district, districtUbigeo, status);
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

    String getPhone() {
        return phone;
    }

    String getAddress() {
        return address;
    }

    String getDistrict() {
        return district;
    }

    String getDistrictUbigeo() {
        return districtUbigeo;
    }

    String getStatus() {
        return status;
    }
}

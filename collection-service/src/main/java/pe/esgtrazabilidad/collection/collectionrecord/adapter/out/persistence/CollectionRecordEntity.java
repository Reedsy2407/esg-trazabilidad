package pe.esgtrazabilidad.collection.collectionrecord.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "collection_record")
class CollectionRecordEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "neighbor_id", nullable = false)
    private UUID neighborId;

    @Column(name = "schedule_id")
    private UUID scheduleId;

    @Column(name = "association_id", nullable = false)
    private UUID associationId;

    @Column(name = "collection_date", nullable = false)
    private LocalDate collectionDate;

    @Column(name = "weight_kg", nullable = false)
    private BigDecimal weightKg;

    @Column(name = "recycler_id")
    private UUID recyclerId;

    @Column(name = "waste_type", length = 32)
    private String wasteType;

    @Transient
    private boolean isNew = false;

    protected CollectionRecordEntity() {
    }

    CollectionRecordEntity(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg,
            UUID recyclerId,
            String wasteType) {
        this.id = id;
        this.neighborId = neighborId;
        this.scheduleId = scheduleId;
        this.associationId = associationId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
        this.recyclerId = recyclerId;
        this.wasteType = wasteType;
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

    UUID getNeighborId() {
        return neighborId;
    }

    UUID getScheduleId() {
        return scheduleId;
    }

    UUID getAssociationId() {
        return associationId;
    }

    LocalDate getCollectionDate() {
        return collectionDate;
    }

    BigDecimal getWeightKg() {
        return weightKg;
    }

    UUID getRecyclerId() {
        return recyclerId;
    }

    String getWasteType() {
        return wasteType;
    }
}

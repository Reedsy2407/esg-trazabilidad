package pe.esgtrazabilidad.collection.schedule.adapter.out.persistence;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "collection_schedule")
class CollectionScheduleEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "neighbor_id", nullable = false)
    private UUID neighborId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "pickup_time", nullable = false)
    private LocalTime time;

    @Column(nullable = false)
    private String status;

    @Transient
    private boolean isNew = false;

    protected CollectionScheduleEntity() {
    }

    CollectionScheduleEntity(UUID id, UUID neighborId, DayOfWeek dayOfWeek, LocalTime time, String status) {
        this.id = id;
        this.neighborId = neighborId;
        this.dayOfWeek = dayOfWeek;
        this.time = time;
        this.status = status;
        this.isNew = true;
    }

    /**
     * For updating a row that's already persisted (pause/cancel/reactivate).
     * Unlike the public constructor (always isNew=true, correct for create()),
     * this produces an entity Spring Data routes through merge() instead of
     * persist() -- see [[persistable_update_path]].
     */
    static CollectionScheduleEntity existing(
            UUID id, UUID neighborId, DayOfWeek dayOfWeek, LocalTime time, String status) {
        CollectionScheduleEntity entity = new CollectionScheduleEntity();
        entity.id = id;
        entity.neighborId = neighborId;
        entity.dayOfWeek = dayOfWeek;
        entity.time = time;
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

    UUID getNeighborId() {
        return neighborId;
    }

    DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    LocalTime getTime() {
        return time;
    }

    String getStatus() {
        return status;
    }
}

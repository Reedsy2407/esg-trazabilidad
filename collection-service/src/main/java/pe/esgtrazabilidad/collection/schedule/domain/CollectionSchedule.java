package pe.esgtrazabilidad.collection.schedule.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class CollectionSchedule {

    private final UUID id;
    private final UUID neighborId;
    private final DayOfWeek dayOfWeek;
    private final LocalTime time;
    private CollectionScheduleStatus status;

    private CollectionSchedule(
            UUID id, UUID neighborId, DayOfWeek dayOfWeek, LocalTime time, CollectionScheduleStatus status) {
        this.id = id;
        this.neighborId = neighborId;
        this.dayOfWeek = dayOfWeek;
        this.time = time;
        this.status = status;
    }

    public static CollectionSchedule create(UUID neighborId, DayOfWeek dayOfWeek, LocalTime time) {
        return new CollectionSchedule(
                IdGenerator.generate(), neighborId, dayOfWeek, time, CollectionScheduleStatus.ACTIVE);
    }

    public static CollectionSchedule reconstruct(
            UUID id, UUID neighborId, DayOfWeek dayOfWeek, LocalTime time, CollectionScheduleStatus status) {
        return new CollectionSchedule(id, neighborId, dayOfWeek, time, status);
    }

    /**
     * ACTIVE -> PAUSED: temporary halt (e.g. the neighbor is away). Reversible via reactivate().
     */
    public void pause() {
        if (status != CollectionScheduleStatus.ACTIVE) {
            throw new IllegalStateException("Solo una programación activa puede pausarse");
        }
        status = CollectionScheduleStatus.PAUSED;
    }

    /**
     * PAUSED -> ACTIVE: resumes a temporarily-halted schedule. Callers must
     * re-run the same-day conflict check (COL-002) before calling this --
     * another ACTIVE schedule could have been created for that day while paused.
     */
    public void reactivate() {
        if (status != CollectionScheduleStatus.PAUSED) {
            throw new IllegalStateException("Solo una programación pausada puede reactivarse");
        }
        status = CollectionScheduleStatus.ACTIVE;
    }

    /**
     * ACTIVE or PAUSED -> CANCELLED: permanent end of this schedule. Terminal --
     * no transition leads back out of CANCELLED; a returning neighbor gets a
     * brand-new CollectionSchedule row instead.
     */
    public void cancel() {
        if (status == CollectionScheduleStatus.CANCELLED) {
            throw new IllegalStateException("La programación ya está cancelada");
        }
        status = CollectionScheduleStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getNeighborId() {
        return neighborId;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getTime() {
        return time;
    }

    public CollectionScheduleStatus getStatus() {
        return status;
    }
}

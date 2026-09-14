package pe.esgtrazabilidad.collection.schedule.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionScheduleTest {

    private final UUID neighborId = UUID.randomUUID();

    private CollectionSchedule newSchedule() {
        return CollectionSchedule.create(neighborId, DayOfWeek.MONDAY, LocalTime.of(9, 0));
    }

    @Test
    void createsAnActiveScheduleWithAGeneratedId() {
        CollectionSchedule schedule = newSchedule();

        assertThat(schedule.getId()).isNotNull();
        assertThat(schedule.getNeighborId()).isEqualTo(neighborId);
        assertThat(schedule.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(schedule.getTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.ACTIVE);
    }

    @Test
    void reconstructRebuildsAnExistingScheduleWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();

        CollectionSchedule schedule = CollectionSchedule.reconstruct(
                id, neighborId, DayOfWeek.TUESDAY, LocalTime.of(10, 0), CollectionScheduleStatus.PAUSED);

        assertThat(schedule.getId()).isEqualTo(id);
        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.PAUSED);
    }

    @Test
    void pauseMovesAnActiveScheduleToPaused() {
        CollectionSchedule schedule = newSchedule();

        schedule.pause();

        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.PAUSED);
    }

    @Test
    void pausingAnAlreadyPausedScheduleThrows() {
        CollectionSchedule schedule = newSchedule();
        schedule.pause();

        assertThatThrownBy(schedule::pause).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void pausingACancelledScheduleThrows() {
        CollectionSchedule schedule = newSchedule();
        schedule.cancel();

        assertThatThrownBy(schedule::pause).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reactivateMovesAPausedScheduleToActive() {
        CollectionSchedule schedule = newSchedule();
        schedule.pause();

        schedule.reactivate();

        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.ACTIVE);
    }

    @Test
    void reactivatingAnActiveScheduleThrows() {
        CollectionSchedule schedule = newSchedule();

        assertThatThrownBy(schedule::reactivate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reactivatingACancelledScheduleThrows() {
        CollectionSchedule schedule = newSchedule();
        schedule.cancel();

        assertThatThrownBy(schedule::reactivate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cancelMovesAnActiveScheduleToCancelled() {
        CollectionSchedule schedule = newSchedule();

        schedule.cancel();

        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.CANCELLED);
    }

    @Test
    void cancelMovesAPausedScheduleToCancelled() {
        CollectionSchedule schedule = newSchedule();
        schedule.pause();

        schedule.cancel();

        assertThat(schedule.getStatus()).isEqualTo(CollectionScheduleStatus.CANCELLED);
    }

    @Test
    void cancellingAnAlreadyCancelledScheduleThrows() {
        CollectionSchedule schedule = newSchedule();
        schedule.cancel();

        assertThatThrownBy(schedule::cancel).isInstanceOf(IllegalStateException.class);
    }
}

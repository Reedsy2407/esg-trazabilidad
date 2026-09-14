package pe.esgtrazabilidad.collection.schedule.service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.collection.schedule.domain.CollectionSchedule;
import pe.esgtrazabilidad.collection.schedule.port.in.CreateCollectionScheduleCommand;
import pe.esgtrazabilidad.collection.schedule.port.out.CollectionScheduleRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionScheduleServiceTest {

    @Mock
    private CollectionScheduleRepository repository;

    @Mock
    private NeighborRepository neighborRepository;

    private CollectionScheduleService service;

    private final UUID neighborId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CollectionScheduleService(repository, neighborRepository);
    }

    private Neighbor sampleNeighbor() {
        return Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco");
    }

    private CreateCollectionScheduleCommand sampleCommand() {
        return new CreateCollectionScheduleCommand(neighborId, DayOfWeek.MONDAY, LocalTime.of(9, 0));
    }

    @Test
    void createsAndSavesANewScheduleWhenNeighborExistsAndNoConflict() {
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.of(sampleNeighbor()));
        when(repository.findActiveByNeighborIdAndDayOfWeek(neighborId, DayOfWeek.MONDAY))
                .thenReturn(Optional.empty());
        when(repository.save(any(CollectionSchedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionSchedule result = service.create(sampleCommand());

        assertThat(result.getNeighborId()).isEqualTo(neighborId);
        assertThat(result.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        verify(repository).save(any(CollectionSchedule.class));
    }

    @Test
    void rejectsCreationWhenNeighborDoesNotExist() {
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.NEIGHBOR_NOT_FOUND));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsCreationWhenAnActiveConflictExistsForTheSameDay() {
        CollectionSchedule existing = CollectionSchedule.create(neighborId, DayOfWeek.MONDAY, LocalTime.of(8, 0));
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.of(sampleNeighbor()));
        when(repository.findActiveByNeighborIdAndDayOfWeek(neighborId, DayOfWeek.MONDAY))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.SCHEDULE_CONFLICT));
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheScheduleWhenFoundByIdScopedToItsNeighbor() {
        CollectionSchedule schedule = CollectionSchedule.create(neighborId, DayOfWeek.MONDAY, LocalTime.of(9, 0));
        when(repository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        CollectionSchedule result = service.getById(neighborId, schedule.getId());

        assertThat(result).isEqualTo(schedule);
    }

    @Test
    void throwsNotFoundWhenScheduleDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(neighborId, missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.SCHEDULE_NOT_FOUND));
    }

    @Test
    void throwsNotFoundWhenScheduleBelongsToADifferentNeighbor() {
        UUID otherNeighborId = UUID.randomUUID();
        CollectionSchedule schedule =
                CollectionSchedule.create(otherNeighborId, DayOfWeek.MONDAY, LocalTime.of(9, 0));
        when(repository.findById(schedule.getId())).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> service.getById(neighborId, schedule.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.SCHEDULE_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<CollectionSchedule> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(neighborId, pageable)).thenReturn(expectedPage);

        Page<CollectionSchedule> result = service.list(neighborId, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

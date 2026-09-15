package pe.esgtrazabilidad.collection.collectionrecord.service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.collection.collectionrecord.port.in.CreateCollectionRecordCommand;
import pe.esgtrazabilidad.collection.collectionrecord.port.out.CollectionRecordRepository;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollectionRecordServiceTest {

    @Mock
    private CollectionRecordRepository repository;

    @Mock
    private NeighborRepository neighborRepository;

    private CollectionRecordService service;

    private final UUID neighborId = UUID.randomUUID();
    private final UUID associationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CollectionRecordService(repository, neighborRepository);
    }

    private Neighbor sampleNeighbor() {
        return Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco");
    }

    private CreateCollectionRecordCommand sampleCommand(UUID scheduleId) {
        return new CreateCollectionRecordCommand(
                neighborId, scheduleId, associationId, LocalDate.now(), new BigDecimal("10"));
    }

    @Test
    void createsAndSavesARecordLinkedToAScheduleWhenNeighborExists() {
        UUID scheduleId = UUID.randomUUID();
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.of(sampleNeighbor()));
        when(repository.save(any(CollectionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionRecord result = service.create(sampleCommand(scheduleId));

        assertThat(result.getScheduleId()).isEqualTo(scheduleId);
        assertThat(result.getNeighborId()).isEqualTo(neighborId);
        verify(repository).save(any(CollectionRecord.class));
    }

    @Test
    void createsAnAdHocRecordWhenScheduleIdIsOmitted() {
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.of(sampleNeighbor()));
        when(repository.save(any(CollectionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionRecord result = service.create(sampleCommand(null));

        assertThat(result.getScheduleId()).isNull();
    }

    @Test
    void createSucceedsWithAnUnvalidatedAssociationId() {
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.of(sampleNeighbor()));
        when(repository.save(any(CollectionRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CollectionRecord result = service.create(sampleCommand(null));

        // No repository/service call ever checks associationId against anything --
        // it's stored exactly as given, proving the eventual-consistency decision holds.
        assertThat(result.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    void rejectsCreationWhenNeighborDoesNotExist() {
        when(neighborRepository.findById(neighborId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sampleCommand(null)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.NEIGHBOR_NOT_FOUND));
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheRecordWhenFoundByIdScopedToItsNeighbor() {
        CollectionRecord record =
                CollectionRecord.create(neighborId, null, associationId, LocalDate.now(), new BigDecimal("10"));
        when(repository.findById(record.getId())).thenReturn(Optional.of(record));

        CollectionRecord result = service.getById(neighborId, record.getId());

        assertThat(result).isEqualTo(record);
    }

    @Test
    void throwsNotFoundWhenRecordDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(neighborId, missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.RECORD_NOT_FOUND));
    }

    @Test
    void throwsNotFoundWhenRecordBelongsToADifferentNeighbor() {
        UUID otherNeighborId = UUID.randomUUID();
        CollectionRecord record = CollectionRecord.create(
                otherNeighborId, null, associationId, LocalDate.now(), new BigDecimal("10"));
        when(repository.findById(record.getId())).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> service.getById(neighborId, record.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.RECORD_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now();
        Page<CollectionRecord> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(neighborId, from, to, pageable)).thenReturn(expectedPage);

        Page<CollectionRecord> result = service.list(neighborId, from, to, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

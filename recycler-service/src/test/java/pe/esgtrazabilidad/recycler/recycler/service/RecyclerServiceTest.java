package pe.esgtrazabilidad.recycler.recycler.service;

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

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.exception.RecyclerErrors;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerCommand;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecyclerServiceTest {

    @Mock
    private RecyclerRepository recyclerRepository;

    @Mock
    private AssociationRepository associationRepository;

    private RecyclerService service;

    private final UUID associationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RecyclerService(recyclerRepository, associationRepository);
    }

    private Association existingAssociation() {
        return Association.create("Asociación", "20123456789", "REG-001", "Dirección", "a@b.pe", "999999999");
    }

    private CreateRecyclerCommand sampleCommand() {
        return new CreateRecyclerCommand("Juan Pérez", "12345678", "999999999", associationId);
    }

    @Test
    void createsAndSavesARecyclerWhenAssociationExistsAndDniIsFree() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(existingAssociation()));
        when(recyclerRepository.findByDni("12345678")).thenReturn(Optional.empty());
        when(recyclerRepository.save(any(Recycler.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Recycler result = service.create(sampleCommand());

        assertThat(result.getDni()).isEqualTo("12345678");
        assertThat(result.getStatus()).isEqualTo(RecyclerStatus.ACTIVE);
        verify(recyclerRepository).save(any(Recycler.class));
    }

    @Test
    void rejectsCreationWhenTheAssociationDoesNotExist() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.ASSOCIATION_NOT_FOUND));
        verify(recyclerRepository, never()).findByDni(any());
        verify(recyclerRepository, never()).save(any());
    }

    @Test
    void rejectsCreationWhenTheDniIsAlreadyTaken() {
        when(associationRepository.findById(associationId)).thenReturn(Optional.of(existingAssociation()));
        Recycler existing = Recycler.create("Otro reciclador", "12345678", "888888888", associationId);
        when(recyclerRepository.findByDni("12345678")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.DUPLICATE_DNI));
        verify(recyclerRepository, never()).save(any());
    }

    @Test
    void returnsTheRecyclerWhenFoundByIdUnderItsOwnAssociation() {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));

        Recycler result = service.getById(associationId, recycler.getId());

        assertThat(result).isEqualTo(recycler);
    }

    @Test
    void throwsNotFoundWhenRecyclerDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(recyclerRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(associationId, missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.NOT_FOUND));
    }

    @Test
    void throwsNotFoundWhenRecyclerBelongsToADifferentAssociation() {
        UUID otherAssociationId = UUID.randomUUID();
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", otherAssociationId);
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));

        assertThatThrownBy(() -> service.getById(associationId, recycler.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.NOT_FOUND));
    }

    @Test
    void deactivateMovesAnActiveRecyclerToInactive() {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));
        when(recyclerRepository.update(any(Recycler.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Recycler result = service.deactivate(associationId, recycler.getId());

        assertThat(result.getStatus()).isEqualTo(RecyclerStatus.INACTIVE);
        verify(recyclerRepository).update(recycler);
    }

    @Test
    void deactivatingAnAlreadyInactiveRecyclerThrowsAConflict() {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
        recycler.deactivate();
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));

        assertThatThrownBy(() -> service.deactivate(associationId, recycler.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.INVALID_STATUS_TRANSITION));
        verify(recyclerRepository, never()).update(any());
    }

    @Test
    void deactivatingARecyclerFromADifferentAssociationThrowsNotFound() {
        UUID otherAssociationId = UUID.randomUUID();
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", otherAssociationId);
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));

        assertThatThrownBy(() -> service.deactivate(associationId, recycler.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.NOT_FOUND));
        verify(recyclerRepository, never()).update(any());
    }

    @Test
    void activateMovesAnInactiveRecyclerToActive() {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
        recycler.deactivate();
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));
        when(recyclerRepository.update(any(Recycler.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Recycler result = service.activate(associationId, recycler.getId());

        assertThat(result.getStatus()).isEqualTo(RecyclerStatus.ACTIVE);
        verify(recyclerRepository).update(recycler);
    }

    @Test
    void activatingAnAlreadyActiveRecyclerThrowsAConflict() {
        Recycler recycler = Recycler.create("Juan Pérez", "12345678", "999999999", associationId);
        when(recyclerRepository.findById(recycler.getId())).thenReturn(Optional.of(recycler));

        assertThatThrownBy(() -> service.activate(associationId, recycler.getId()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(RecyclerErrors.INVALID_STATUS_TRANSITION));
        verify(recyclerRepository, never()).update(any());
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Recycler> expectedPage = new PageImpl<>(java.util.List.of());
        when(recyclerRepository.findAll(associationId, RecyclerStatus.ACTIVE, pageable)).thenReturn(expectedPage);

        Page<Recycler> result = service.list(associationId, RecyclerStatus.ACTIVE, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

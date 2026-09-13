package pe.esgtrazabilidad.recycler.association.service;

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
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;
import pe.esgtrazabilidad.recycler.association.exception.AssociationErrors;
import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationCommand;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssociationServiceTest {

    @Mock
    private AssociationRepository repository;

    private AssociationService service;

    @BeforeEach
    void setUp() {
        service = new AssociationService(repository);
    }

    private CreateAssociationCommand sampleCommand() {
        return new CreateAssociationCommand(
                "Asociación de prueba", "20123456789", "REG-001", "Dirección 123", "a@b.pe", "999999999");
    }

    @Test
    void createsAndSavesANewAssociationWhenRucIsNotTaken() {
        when(repository.findByRuc("20123456789")).thenReturn(Optional.empty());
        when(repository.save(any(Association.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Association result = service.create(sampleCommand());

        assertThat(result.getRuc()).isEqualTo("20123456789");
        assertThat(result.getStatus()).isEqualTo(AssociationStatus.ACTIVE);
        verify(repository).save(any(Association.class));
    }

    @Test
    void rejectsCreationWhenRucIsAlreadyTaken() {
        Association existing = Association.create(
                "Otra asociación", "20123456789", "REG-002", "Otra dirección", "c@d.pe", "888888888");
        when(repository.findByRuc("20123456789")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(AssociationErrors.DUPLICATE_RUC));
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheAssociationWhenFoundById() {
        Association association = Association.create(
                "Asociación", "20123456789", "REG-001", "Dirección", "a@b.pe", "999999999");
        when(repository.findById(association.getId())).thenReturn(Optional.of(association));

        Association result = service.getById(association.getId());

        assertThat(result).isEqualTo(association);
    }

    @Test
    void throwsNotFoundWhenAssociationDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(AssociationErrors.NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Association> expectedPage = new PageImpl<>(java.util.List.of());
        when(repository.findAll(AssociationStatus.ACTIVE, pageable)).thenReturn(expectedPage);

        Page<Association> result = service.list(AssociationStatus.ACTIVE, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

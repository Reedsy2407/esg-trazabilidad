package pe.esgtrazabilidad.reporting.sigersolsync.service;

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

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.reporting.exception.ReportingErrors;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.RegisterSigersolSyncCommand;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SigersolSyncServiceTest {

    @Mock
    private SigersolSyncRepository repository;

    private SigersolSyncService service;

    @BeforeEach
    void setUp() {
        service = new SigersolSyncService(repository);
    }

    private RegisterSigersolSyncCommand sampleCommand(UUID associationId) {
        return new RegisterSigersolSyncCommand(
                associationId,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("80.00"),
                null,
                null);
    }

    @Test
    void registersAndSavesANewSigersolSyncWhenNoOverlapExists() {
        UUID associationId = UUID.randomUUID();
        when(repository.existsOverlapping(associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(false);
        when(repository.save(any(SigersolSync.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SigersolSync result = service.register(sampleCommand(associationId));

        assertThat(result.getAssociationId()).isEqualTo(associationId);
        verify(repository).save(any(SigersolSync.class));
    }

    @Test
    void rejectsRegistrationWhenAnOverlappingPeriodAlreadyExists() {
        UUID associationId = UUID.randomUUID();
        when(repository.existsOverlapping(associationId, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.register(sampleCommand(associationId)))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.DUPLICATE_SIGERSOL_SYNC_PERIOD));
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnInvalidPeriodWithATypedError() {
        UUID associationId = UUID.randomUUID();
        RegisterSigersolSyncCommand invalidPeriod = new RegisterSigersolSyncCommand(
                associationId, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1), new BigDecimal("80"), null, null);
        when(repository.existsOverlapping(associationId, LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 1)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.register(invalidPeriod))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.INVALID_SIGERSOL_SYNC_DATA));
    }

    @Test
    void returnsTheSigersolSyncWhenFoundById() {
        SigersolSync sigersolSync = SigersolSync.create(
                UUID.randomUUID(), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("80"), null, null);
        when(repository.findById(sigersolSync.getId())).thenReturn(Optional.of(sigersolSync));

        SigersolSync result = service.getById(sigersolSync.getId());

        assertThat(result).isEqualTo(sigersolSync);
    }

    @Test
    void throwsNotFoundWhenSigersolSyncDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.SIGERSOL_SYNC_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<SigersolSync> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(pageable)).thenReturn(expectedPage);

        Page<SigersolSync> result = service.list(pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

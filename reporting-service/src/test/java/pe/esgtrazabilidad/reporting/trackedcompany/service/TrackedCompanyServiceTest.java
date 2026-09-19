package pe.esgtrazabilidad.reporting.trackedcompany.service;

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
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrackedCompanyServiceTest {

    @Mock
    private TrackedCompanyRepository repository;

    private TrackedCompanyService service;

    @BeforeEach
    void setUp() {
        service = new TrackedCompanyService(repository);
    }

    private RegisterTrackedCompanyCommand sampleCommand() {
        return new RegisterTrackedCompanyCommand("Empresa de prueba", "20123456789", UUID.randomUUID());
    }

    @Test
    void registersAndSavesANewTrackedCompanyWhenRucIsNotTaken() {
        when(repository.findByRuc("20123456789")).thenReturn(Optional.empty());
        when(repository.save(any(TrackedCompany.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TrackedCompany result = service.register(sampleCommand());

        assertThat(result.getRuc()).isEqualTo("20123456789");
        verify(repository).save(any(TrackedCompany.class));
    }

    @Test
    void rejectsRegistrationWhenRucIsAlreadyTaken() {
        TrackedCompany existing = TrackedCompany.create("Otra empresa", "20123456789", UUID.randomUUID());
        when(repository.findByRuc("20123456789")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.register(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.DUPLICATE_TRACKED_COMPANY_RUC));
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheTrackedCompanyWhenFoundById() {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa", "20123456789", UUID.randomUUID());
        when(repository.findById(trackedCompany.getId())).thenReturn(Optional.of(trackedCompany));

        TrackedCompany result = service.getById(trackedCompany.getId());

        assertThat(result).isEqualTo(trackedCompany);
    }

    @Test
    void throwsNotFoundWhenTrackedCompanyDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(ReportingErrors.TRACKED_COMPANY_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<TrackedCompany> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(pageable)).thenReturn(expectedPage);

        Page<TrackedCompany> result = service.list(pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

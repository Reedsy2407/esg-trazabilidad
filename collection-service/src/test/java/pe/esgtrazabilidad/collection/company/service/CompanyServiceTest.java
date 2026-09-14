package pe.esgtrazabilidad.collection.company.service;

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

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;
import pe.esgtrazabilidad.collection.company.port.in.CreateCompanyCommand;
import pe.esgtrazabilidad.collection.company.port.out.CompanyRepository;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    @Mock
    private CompanyRepository repository;

    private CompanyService service;

    @BeforeEach
    void setUp() {
        service = new CompanyService(repository);
    }

    private CreateCompanyCommand sampleCommand() {
        return new CreateCompanyCommand("Empresa de prueba", "20123456789", "a@b.pe", "999999999", "Dirección 123");
    }

    @Test
    void createsAndSavesANewCompanyWhenRucIsNotTaken() {
        when(repository.findByRuc("20123456789")).thenReturn(Optional.empty());
        when(repository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company result = service.create(sampleCommand());

        assertThat(result.getRuc()).isEqualTo("20123456789");
        assertThat(result.getStatus()).isEqualTo(CompanyStatus.ACTIVE);
        verify(repository).save(any(Company.class));
    }

    @Test
    void rejectsCreationWhenRucIsAlreadyTaken() {
        Company existing = Company.create("Otra empresa", "20123456789", "c@d.pe", "888888888", "Otra dirección");
        when(repository.findByRuc("20123456789")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(sampleCommand()))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.DUPLICATE_RUC));
        verify(repository, never()).save(any());
    }

    @Test
    void returnsTheCompanyWhenFoundById() {
        Company company = Company.create("Empresa", "20123456789", "a@b.pe", "999999999", "Dirección");
        when(repository.findById(company.getId())).thenReturn(Optional.of(company));

        Company result = service.getById(company.getId());

        assertThat(result).isEqualTo(company);
    }

    @Test
    void throwsNotFoundWhenCompanyDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(missingId))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getError())
                        .isEqualTo(CollectionErrors.COMPANY_NOT_FOUND));
    }

    @Test
    void listDelegatesToTheRepository() {
        Pageable pageable = Pageable.ofSize(10);
        Page<Company> expectedPage = new PageImpl<>(List.of());
        when(repository.findAll(CompanyStatus.ACTIVE, pageable)).thenReturn(expectedPage);

        Page<Company> result = service.list(CompanyStatus.ACTIVE, pageable);

        assertThat(result).isSameAs(expectedPage);
    }
}

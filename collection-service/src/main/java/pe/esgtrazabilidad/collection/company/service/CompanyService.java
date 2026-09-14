package pe.esgtrazabilidad.collection.company.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;
import pe.esgtrazabilidad.collection.company.port.in.CreateCompanyCommand;
import pe.esgtrazabilidad.collection.company.port.in.CreateCompanyUseCase;
import pe.esgtrazabilidad.collection.company.port.in.GetCompanyUseCase;
import pe.esgtrazabilidad.collection.company.port.in.ListCompaniesUseCase;
import pe.esgtrazabilidad.collection.company.port.out.CompanyRepository;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class CompanyService implements CreateCompanyUseCase, GetCompanyUseCase, ListCompaniesUseCase {

    private final CompanyRepository repository;

    CompanyService(CompanyRepository repository) {
        this.repository = repository;
    }

    @Override
    public Company create(CreateCompanyCommand command) {
        repository.findByRuc(command.ruc()).ifPresent(existing -> {
            throw new ApplicationException(CollectionErrors.DUPLICATE_RUC);
        });
        Company company = Company.create(
                command.name(), command.ruc(), command.contactEmail(), command.contactPhone(), command.address());
        return repository.save(company);
    }

    @Override
    public Company getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ApplicationException(CollectionErrors.COMPANY_NOT_FOUND));
    }

    @Override
    public Page<Company> list(CompanyStatus status, Pageable pageable) {
        return repository.findAll(status, pageable);
    }
}

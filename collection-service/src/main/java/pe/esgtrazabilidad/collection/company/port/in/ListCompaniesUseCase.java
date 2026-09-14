package pe.esgtrazabilidad.collection.company.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;

public interface ListCompaniesUseCase {

    Page<Company> list(CompanyStatus status, Pageable pageable);
}

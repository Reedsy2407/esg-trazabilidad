package pe.esgtrazabilidad.collection.company.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;

public interface CompanyRepository {

    Company save(Company company);

    Optional<Company> findById(UUID id);

    Optional<Company> findByRuc(String ruc);

    Page<Company> findAll(CompanyStatus status, Pageable pageable);
}

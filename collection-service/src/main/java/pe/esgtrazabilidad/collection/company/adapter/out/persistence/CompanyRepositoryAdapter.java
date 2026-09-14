package pe.esgtrazabilidad.collection.company.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;
import pe.esgtrazabilidad.collection.company.port.out.CompanyRepository;

@Component
class CompanyRepositoryAdapter implements CompanyRepository {

    private final CompanyJpaRepository jpaRepository;

    CompanyRepositoryAdapter(CompanyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Company save(Company company) {
        return toDomain(jpaRepository.save(toEntity(company)));
    }

    @Override
    public Optional<Company> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Company> findByRuc(String ruc) {
        return jpaRepository.findByRuc(ruc).map(this::toDomain);
    }

    @Override
    public Page<Company> findAll(CompanyStatus status, Pageable pageable) {
        String statusValue = status == null ? null : status.name();
        return jpaRepository.findAll(CompanySpecifications.hasStatus(statusValue), pageable).map(this::toDomain);
    }

    private CompanyEntity toEntity(Company company) {
        return new CompanyEntity(
                company.getId(),
                company.getName(),
                company.getRuc(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getAddress(),
                company.getStatus().name());
    }

    private Company toDomain(CompanyEntity entity) {
        return Company.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getRuc(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                entity.getAddress(),
                CompanyStatus.valueOf(entity.getStatus()));
    }
}

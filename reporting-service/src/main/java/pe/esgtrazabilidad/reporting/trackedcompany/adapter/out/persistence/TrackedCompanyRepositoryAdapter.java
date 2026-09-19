package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Component
class TrackedCompanyRepositoryAdapter implements TrackedCompanyRepository {

    private final TrackedCompanyJpaRepository jpaRepository;

    TrackedCompanyRepositoryAdapter(TrackedCompanyJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public TrackedCompany save(TrackedCompany trackedCompany) {
        return toDomain(jpaRepository.save(toEntity(trackedCompany)));
    }

    @Override
    public Optional<TrackedCompany> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<TrackedCompany> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    private TrackedCompanyEntity toEntity(TrackedCompany trackedCompany) {
        return new TrackedCompanyEntity(
                trackedCompany.getId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                trackedCompany.getAssociationId(),
                trackedCompany.getStatus().name());
    }

    private TrackedCompany toDomain(TrackedCompanyEntity entity) {
        return TrackedCompany.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getRuc(),
                entity.getAssociationId(),
                TrackedCompanyStatus.valueOf(entity.getStatus()));
    }
}

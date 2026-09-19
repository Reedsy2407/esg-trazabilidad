package pe.esgtrazabilidad.reporting.sigersolsync.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;

@Component
class SigersolSyncRepositoryAdapter implements SigersolSyncRepository {

    private final SigersolSyncJpaRepository jpaRepository;

    SigersolSyncRepositoryAdapter(SigersolSyncJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public SigersolSync save(SigersolSync sigersolSync) {
        return toDomain(jpaRepository.save(toEntity(sigersolSync)));
    }

    @Override
    public Optional<SigersolSync> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<SigersolSync> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable).map(this::toDomain);
    }

    private SigersolSyncEntity toEntity(SigersolSync sigersolSync) {
        return new SigersolSyncEntity(
                sigersolSync.getId(),
                sigersolSync.getAssociationId(),
                sigersolSync.getPeriodStart(),
                sigersolSync.getPeriodEnd(),
                sigersolSync.getHierarchyCompliancePercent(),
                sigersolSync.getOfficialKilosDeclared(),
                sigersolSync.getDeclaredAt(),
                sigersolSync.getSourceNote());
    }

    private SigersolSync toDomain(SigersolSyncEntity entity) {
        return SigersolSync.reconstruct(
                entity.getId(),
                entity.getAssociationId(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getHierarchyCompliancePercent(),
                entity.getOfficialKilosDeclared(),
                entity.getDeclaredAt(),
                entity.getSourceNote());
    }
}

package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

@Component
class AssociationRepositoryAdapter implements AssociationRepository {

    private final AssociationJpaRepository jpaRepository;

    AssociationRepositoryAdapter(AssociationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Association save(Association association) {
        return toDomain(jpaRepository.save(toEntity(association)));
    }

    @Override
    public Association update(Association association) {
        AssociationEntity existing = AssociationEntity.existing(
                association.getId(),
                association.getName(),
                association.getRuc(),
                association.getRegistrationNumber(),
                association.getAddress(),
                association.getContactEmail(),
                association.getContactPhone(),
                association.getStatus().name());
        return toDomain(jpaRepository.save(existing));
    }

    @Override
    public Optional<Association> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Association> findByRuc(String ruc) {
        return jpaRepository.findByRuc(ruc).map(this::toDomain);
    }

    @Override
    public Page<Association> findAll(AssociationStatus status, Pageable pageable) {
        String statusValue = status == null ? null : status.name();
        return jpaRepository.findAll(AssociationSpecifications.hasStatus(statusValue), pageable)
                .map(this::toDomain);
    }

    private AssociationEntity toEntity(Association association) {
        return new AssociationEntity(
                association.getId(),
                association.getName(),
                association.getRuc(),
                association.getRegistrationNumber(),
                association.getAddress(),
                association.getContactEmail(),
                association.getContactPhone(),
                association.getStatus().name());
    }

    private Association toDomain(AssociationEntity entity) {
        return Association.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getRuc(),
                entity.getRegistrationNumber(),
                entity.getAddress(),
                entity.getContactEmail(),
                entity.getContactPhone(),
                AssociationStatus.valueOf(entity.getStatus()));
    }
}

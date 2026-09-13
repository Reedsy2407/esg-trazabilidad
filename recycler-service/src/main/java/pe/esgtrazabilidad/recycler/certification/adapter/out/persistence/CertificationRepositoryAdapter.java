package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

@Component
class CertificationRepositoryAdapter implements CertificationRepository {

    private final CertificationJpaRepository jpaRepository;

    CertificationRepositoryAdapter(CertificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Certification save(Certification certification) {
        return toDomain(jpaRepository.save(toEntity(certification)));
    }

    @Override
    public Certification update(Certification certification) {
        CertificationEntity existing = CertificationEntity.existing(
                certification.getId(),
                certification.getAssociationId(),
                certification.getCertificationType(),
                certification.getIssuedDate(),
                certification.getExpirationDate());
        return toDomain(jpaRepository.save(existing));
    }

    @Override
    public Optional<Certification> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Page<Certification> findAll(UUID associationId, Pageable pageable) {
        return jpaRepository
                .findAll(CertificationSpecifications.hasAssociationId(associationId), pageable)
                .map(this::toDomain);
    }

    private CertificationEntity toEntity(Certification certification) {
        return new CertificationEntity(
                certification.getId(),
                certification.getAssociationId(),
                certification.getCertificationType(),
                certification.getIssuedDate(),
                certification.getExpirationDate());
    }

    private Certification toDomain(CertificationEntity entity) {
        return Certification.reconstruct(
                entity.getId(),
                entity.getAssociationId(),
                entity.getCertificationType(),
                entity.getIssuedDate(),
                entity.getExpirationDate());
    }
}

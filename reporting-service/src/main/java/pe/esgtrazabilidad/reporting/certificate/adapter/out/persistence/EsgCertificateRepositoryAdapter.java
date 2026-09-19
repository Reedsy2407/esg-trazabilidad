package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;

@Component
class EsgCertificateRepositoryAdapter implements EsgCertificateRepository {

    private final EsgCertificateJpaRepository certificateJpaRepository;
    private final EsgCertificateLineItemJpaRepository lineItemJpaRepository;

    EsgCertificateRepositoryAdapter(
            EsgCertificateJpaRepository certificateJpaRepository,
            EsgCertificateLineItemJpaRepository lineItemJpaRepository) {
        this.certificateJpaRepository = certificateJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
    }

    @Override
    @Transactional
    public EsgCertificate save(EsgCertificate certificate, List<EsgCertificateLineItem> lineItems) {
        // The certificate row is saved (and flushed) FIRST, deliberately:
        // if it's the one that violates the EXCLUDE constraint (RPT-004),
        // the exception fires here, before any line item is ever written --
        // no partial state to worry about either way, since both writes
        // share this one @Transactional method's single transaction.
        EsgCertificateEntity savedCertificate = certificateJpaRepository.saveAndFlush(toEntity(certificate));
        List<EsgCertificateLineItemEntity> lineItemEntities = lineItems.stream()
                .map(lineItem -> new EsgCertificateLineItemEntity(
                        lineItem.getId(), lineItem.getCertificateId(), lineItem.getCollectionDate(), lineItem.getWeightKg()))
                .toList();
        lineItemJpaRepository.saveAll(lineItemEntities);
        return toDomain(savedCertificate);
    }

    @Override
    public Optional<EsgCertificate> findById(UUID id) {
        return certificateJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<EsgCertificateLineItem> findLineItems(UUID certificateId) {
        return lineItemJpaRepository.findByCertificateId(certificateId).stream()
                .map(entity -> EsgCertificateLineItem.reconstruct(
                        entity.getId(), entity.getCertificateId(), entity.getCollectionDate(), entity.getWeightKg()))
                .toList();
    }

    @Override
    public Page<EsgCertificate> findAll(UUID trackedCompanyId, Pageable pageable) {
        return certificateJpaRepository.findByTrackedCompanyId(trackedCompanyId, pageable).map(this::toDomain);
    }

    @Override
    public boolean existsOverlapping(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd) {
        return certificateJpaRepository.existsOverlapping(trackedCompanyId, periodStart, periodEnd);
    }

    private EsgCertificateEntity toEntity(EsgCertificate certificate) {
        return new EsgCertificateEntity(
                certificate.getId(),
                certificate.getTrackedCompanyId(),
                certificate.getAssociationId(),
                certificate.getCompanyName(),
                certificate.getCompanyRuc(),
                certificate.getPeriodStart(),
                certificate.getPeriodEnd(),
                certificate.getKilosTrazados(),
                certificate.getHierarchyCompliancePercent(),
                certificate.getIssuedAt());
    }

    private EsgCertificate toDomain(EsgCertificateEntity entity) {
        return EsgCertificate.reconstruct(
                entity.getId(),
                entity.getTrackedCompanyId(),
                entity.getAssociationId(),
                entity.getCompanyName(),
                entity.getCompanyRuc(),
                entity.getPeriodStart(),
                entity.getPeriodEnd(),
                entity.getKilosTrazados(),
                entity.getHierarchyCompliancePercent(),
                entity.getIssuedAt());
    }
}

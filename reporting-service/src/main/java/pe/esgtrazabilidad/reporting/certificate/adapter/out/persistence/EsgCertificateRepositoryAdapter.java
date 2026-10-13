package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.port.out.EsgCertificateRepository;

@Component
class EsgCertificateRepositoryAdapter implements EsgCertificateRepository {

    private final EsgCertificateJpaRepository certificateJpaRepository;
    private final EsgCertificateLineItemJpaRepository lineItemJpaRepository;
    private final JdbcTemplate jdbc;

    EsgCertificateRepositoryAdapter(
            EsgCertificateJpaRepository certificateJpaRepository,
            EsgCertificateLineItemJpaRepository lineItemJpaRepository,
            JdbcTemplate jdbc) {
        this.certificateJpaRepository = certificateJpaRepository;
        this.lineItemJpaRepository = lineItemJpaRepository;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public EsgCertificate save(
            EsgCertificate certificate, List<EsgCertificateLineItem> lineItems, List<DistrictLine> districtLines) {
        // The certificate row is saved (and flushed) FIRST, deliberately:
        // if it's the one that violates the EXCLUDE constraint (RPT-004),
        // the exception fires here, before any line item is ever written --
        // no partial state to worry about either way, since both writes
        // share this one @Transactional method's single transaction.
        EsgCertificateEntity savedCertificate = certificateJpaRepository.saveAndFlush(toEntity(certificate));
        List<EsgCertificateLineItemEntity> lineItemEntities = lineItems.stream()
                .map(lineItem -> new EsgCertificateLineItemEntity(
                        lineItem.getId(),
                        lineItem.getCertificateId(),
                        lineItem.getCollectionDate(),
                        lineItem.getWeightKg(),
                        lineItem.getDistrictUbigeo()))
                .toList();
        lineItemJpaRepository.saveAll(lineItemEntities);
        // Frozen with the certificate (flushed first, so the foreign key holds): re-exporting never asks SIGERSOL.
        for (DistrictLine line : districtLines) {
            jdbc.update(
                    "INSERT INTO esg_certificate_district_context (id, certificate_id, district_ubigeo, district_name, "
                            + "kilos, status, year, association_count, recycler_count) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    IdGenerator.generate(),
                    certificate.getId(),
                    line.districtUbigeo(),
                    line.districtName(),
                    line.kilos(),
                    line.status().name(),
                    line.year(),
                    line.associationCount(),
                    line.recyclerCount());
        }
        return toDomain(savedCertificate);
    }

    @Override
    public List<DistrictLine> findDistrictLines(UUID certificateId) {
        return jdbc.query(
                "SELECT district_ubigeo, district_name, kilos, status, year, association_count, recycler_count "
                        + "FROM esg_certificate_district_context WHERE certificate_id = ? "
                        + "ORDER BY (district_ubigeo IS NULL), district_name",
                (rs, rowNum) -> new DistrictLine(
                        rs.getString("district_ubigeo"),
                        rs.getString("district_name"),
                        rs.getBigDecimal("kilos"),
                        DistrictLine.Status.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("year"),
                        (Integer) rs.getObject("association_count"),
                        (Integer) rs.getObject("recycler_count")),
                certificateId);
    }

    @Override
    public Optional<EsgCertificate> findById(UUID id) {
        return certificateJpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<EsgCertificateLineItem> findLineItems(UUID certificateId) {
        return lineItemJpaRepository.findByCertificateId(certificateId).stream()
                .map(entity -> EsgCertificateLineItem.reconstruct(
                        entity.getId(),
                        entity.getCertificateId(),
                        entity.getCollectionDate(),
                        entity.getWeightKg(),
                        entity.getDistrictUbigeo()))
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

    @Override
    @Transactional
    public boolean annul(UUID id, String reason, CurrentActor actor, Instant at) {
        return certificateJpaRepository.annulIfIssued(id, reason, actor.id(), actor.email(), at) == 1;
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
                certificate.getIssuedAt(),
                certificate.getStatus());
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
                entity.getIssuedAt(),
                entity.getStatus(),
                entity.getAnnulledAt(),
                entity.getAnnulledById(),
                entity.getAnnulledByEmail(),
                entity.getAnnulmentReason());
    }
}

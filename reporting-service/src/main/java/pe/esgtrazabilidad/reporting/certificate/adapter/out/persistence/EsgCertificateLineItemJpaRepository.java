package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface EsgCertificateLineItemJpaRepository extends JpaRepository<EsgCertificateLineItemEntity, UUID> {

    List<EsgCertificateLineItemEntity> findByCertificateId(UUID certificateId);
}

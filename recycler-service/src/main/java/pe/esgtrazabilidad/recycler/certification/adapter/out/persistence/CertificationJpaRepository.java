package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CertificationJpaRepository
        extends JpaRepository<CertificationEntity, UUID>, JpaSpecificationExecutor<CertificationEntity> {
}

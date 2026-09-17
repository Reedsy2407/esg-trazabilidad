package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface CertificationJpaRepository
        extends JpaRepository<CertificationEntity, UUID>, JpaSpecificationExecutor<CertificationEntity> {

    @Query("SELECT c FROM CertificationEntity c WHERE c.expirationDate < CURRENT_DATE AND c.notifiedExpiredAt IS NULL")
    List<CertificationEntity> findExpiredAndNotYetNotified();
}

package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EsgCertificateJpaRepository extends JpaRepository<EsgCertificateEntity, UUID> {

    Page<EsgCertificateEntity> findByTrackedCompanyId(UUID trackedCompanyId, Pageable pageable);

    @Query("""
            SELECT COUNT(c) > 0 FROM EsgCertificateEntity c
            WHERE c.trackedCompanyId = :trackedCompanyId
              AND c.periodStart <= :periodEnd
              AND c.periodEnd >= :periodStart
            """)
    boolean existsOverlapping(
            @Param("trackedCompanyId") UUID trackedCompanyId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}

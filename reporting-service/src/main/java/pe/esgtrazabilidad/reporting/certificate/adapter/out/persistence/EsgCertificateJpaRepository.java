package pe.esgtrazabilidad.reporting.certificate.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface EsgCertificateJpaRepository extends JpaRepository<EsgCertificateEntity, UUID> {

    Page<EsgCertificateEntity> findByTrackedCompanyId(UUID trackedCompanyId, Pageable pageable);

    @Query("""
            SELECT COUNT(c) > 0 FROM EsgCertificateEntity c
            WHERE c.trackedCompanyId = :trackedCompanyId
              AND c.periodStart <= :periodEnd
              AND c.periodEnd >= :periodStart
              AND c.status = pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus.ISSUED
            """)
    boolean existsOverlapping(
            @Param("trackedCompanyId") UUID trackedCompanyId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    /**
     * The whole transition is this one conditional UPDATE: of two concurrent
     * annulments exactly one finds the row still ISSUED (the other waits on the
     * row lock, then re-evaluates the WHERE and matches nothing).
     *
     * clearAutomatically matters: the caller re-reads the row afterwards in the
     * same transaction, and without it would get the stale ISSUED entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE EsgCertificateEntity c
            SET c.status = pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus.ANNULLED,
                c.annulledAt = :at, c.annulledById = :actorId, c.annulledByEmail = :actorEmail,
                c.annulmentReason = :reason
            WHERE c.id = :id
              AND c.status = pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus.ISSUED
            """)
    int annulIfIssued(
            @Param("id") UUID id,
            @Param("reason") String reason,
            @Param("actorId") UUID actorId,
            @Param("actorEmail") String actorEmail,
            @Param("at") Instant at);
}

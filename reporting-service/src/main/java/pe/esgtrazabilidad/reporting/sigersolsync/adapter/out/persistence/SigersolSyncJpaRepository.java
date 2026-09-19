package pe.esgtrazabilidad.reporting.sigersolsync.adapter.out.persistence;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SigersolSyncJpaRepository extends JpaRepository<SigersolSyncEntity, UUID> {

    @Query("""
            SELECT COUNT(s) > 0 FROM SigersolSyncEntity s
            WHERE s.associationId = :associationId
              AND s.periodStart <= :periodEnd
              AND s.periodEnd >= :periodStart
            """)
    boolean existsOverlapping(
            @Param("associationId") UUID associationId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);

    @Query("""
            SELECT s FROM SigersolSyncEntity s
            WHERE s.associationId = :associationId
              AND s.periodStart <= :periodStart
              AND s.periodEnd >= :periodEnd
            """)
    Optional<SigersolSyncEntity> findCovering(
            @Param("associationId") UUID associationId,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd);
}

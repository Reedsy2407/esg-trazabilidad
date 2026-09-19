package pe.esgtrazabilidad.reporting.sigersolsync.port.out;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;

public interface SigersolSyncRepository {

    SigersolSync save(SigersolSync sigersolSync);

    Optional<SigersolSync> findById(UUID id);

    Page<SigersolSync> findAll(Pageable pageable);

    /**
     * Fast service-level check before the DB's own EXCLUDE constraint would
     * reject an overlapping insert -- same "quick check first, DB constraint
     * as the real backstop" shape as TrackedCompany's RUC check.
     */
    boolean existsOverlapping(UUID associationId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * A record whose OWN period fully contains [periodStart, periodEnd] --
     * used by CertificateService (Phase 22) to decide whether official
     * compliance data backs a requested certificate period. Deliberately
     * different from existsOverlapping: a SigersolSync record that only
     * partially overlaps a certificate's period isn't sufficient backing.
     */
    Optional<SigersolSync> findCovering(UUID associationId, LocalDate periodStart, LocalDate periodEnd);
}

package pe.esgtrazabilidad.reporting.certificate.port.out;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;

public interface EsgCertificateRepository {

    /**
     * Persists the certificate AND freezes its contributing line items in
     * one transaction -- either both are durable or neither is.
     */
    default EsgCertificate save(EsgCertificate certificate, List<EsgCertificateLineItem> lineItems) {
        return save(certificate, lineItems, List.of());
    }

    /** Same, and freezes the SIGERSOL district context of the certificate in the same transaction. */
    EsgCertificate save(
            EsgCertificate certificate, List<EsgCertificateLineItem> lineItems, List<DistrictLine> districtLines);

    /** The district lines frozen at issue, by district name, the unknown district last. */
    List<DistrictLine> findDistrictLines(UUID certificateId);

    Optional<EsgCertificate> findById(UUID id);

    List<EsgCertificateLineItem> findLineItems(UUID certificateId);

    Page<EsgCertificate> findAll(UUID trackedCompanyId, Pageable pageable);

    /**
     * Fast service-level check before the DB's own EXCLUDE constraint would
     * reject an overlapping insert -- same "quick check first, DB constraint
     * as the real backstop" shape as SigersolSync's own existsOverlapping.
     */
    boolean existsOverlapping(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd);

    /**
     * Conditional ISSUED -> ANNULLED transition. Returns false when the
     * certificate was no longer ISSUED (already annulled, possibly a moment
     * ago by someone else), true when this call performed it.
     */
    boolean annul(UUID id, String reason, CurrentActor actor, Instant at);
}

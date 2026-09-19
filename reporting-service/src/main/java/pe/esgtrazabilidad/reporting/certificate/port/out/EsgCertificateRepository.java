package pe.esgtrazabilidad.reporting.certificate.port.out;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;

public interface EsgCertificateRepository {

    /**
     * Persists the certificate AND freezes its contributing line items in
     * one transaction -- either both are durable or neither is.
     */
    EsgCertificate save(EsgCertificate certificate, List<EsgCertificateLineItem> lineItems);

    Optional<EsgCertificate> findById(UUID id);

    List<EsgCertificateLineItem> findLineItems(UUID certificateId);

    Page<EsgCertificate> findAll(UUID trackedCompanyId, Pageable pageable);

    /**
     * Fast service-level check before the DB's own EXCLUDE constraint would
     * reject an overlapping insert -- same "quick check first, DB constraint
     * as the real backstop" shape as SigersolSync's own existsOverlapping.
     */
    boolean existsOverlapping(UUID trackedCompanyId, LocalDate periodStart, LocalDate periodEnd);
}

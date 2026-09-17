package pe.esgtrazabilidad.recycler.certification.job;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.events.CertificationExpiredEvent;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;
import pe.esgtrazabilidad.recycler.events.publish.CertificationExpiredEventPublisher;

/**
 * A separate Spring bean from CertificationExpiryScanJob, not a private
 * method on it -- same self-invocation reasoning as
 * CollectionRegisteredEventProcessor (Task 30): {@code @Transactional} only
 * takes effect through Spring's proxy.
 */
@Component
class CertificationExpiryProcessor {

    private final CertificationRepository certificationRepository;
    private final CertificationExpiredEventPublisher publisher;

    CertificationExpiryProcessor(
            CertificationRepository certificationRepository, CertificationExpiredEventPublisher publisher) {
        this.certificationRepository = certificationRepository;
        this.publisher = publisher;
    }

    @Transactional
    void process(Certification certification) {
        publisher.publish(CertificationExpiredEvent.from(certification));
        certification.markNotifiedExpired(Instant.now());
        certificationRepository.update(certification);
    }
}

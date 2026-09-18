package pe.esgtrazabilidad.collection.events.consume;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;
import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;
import pe.esgtrazabilidad.collection.events.ledger.CertificationStatusLedgerEntity;
import pe.esgtrazabilidad.collection.events.ledger.CertificationStatusLedgerJpaRepository;

/**
 * A separate Spring bean from the listener, not a private method on it:
 * {@code @Transactional} only takes effect through Spring's proxy, which
 * self-invocation bypasses entirely -- same reasoning as
 * CollectionRegisteredEventProcessor (Task 30).
 *
 * <p>The ledger insert is deliberately LAST, flushed explicitly, inside the
 * same transaction as the block/unblock write -- not a try/catch around an
 * early ledger insert. A duplicate-key violation here (either this event's
 * own event_id on redelivery, OR a concurrent different event racing to
 * block the same associationId for the first time -- see
 * BlockedAssociationRepositoryAdapter.block()'s check-then-act, a
 * user-flagged finding from Task 35) fails the flush, which rolls back this
 * entire transaction and rethrows to the caller. The listener's own
 * try/catch DataIntegrityViolationException (mirroring Task 30's pattern)
 * covers BOTH cases identically: either way, this specific delivery
 * attempt's side effects roll back cleanly with no savepoints needed, and
 * the association ends up in the correct blocked/unblocked state regardless
 * of which concurrent attempt "won".
 */
@Component
class CertificationStatusEventProcessor {

    private final BlockedAssociationRepository blockedAssociationRepository;
    private final CertificationStatusLedgerJpaRepository ledgerRepository;

    CertificationStatusEventProcessor(
            BlockedAssociationRepository blockedAssociationRepository,
            CertificationStatusLedgerJpaRepository ledgerRepository) {
        this.blockedAssociationRepository = blockedAssociationRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    void processExpired(CertificationExpiredEvent event) {
        blockedAssociationRepository.block(BlockedAssociation.block(event.associationId(), Instant.now()));
        ledgerRepository.saveAndFlush(new CertificationStatusLedgerEntity(event.eventId(), Instant.now()));
    }

    @Transactional
    void processRenewed(CertificationRenewedEvent event) {
        blockedAssociationRepository.unblock(event.associationId());
        ledgerRepository.saveAndFlush(new CertificationStatusLedgerEntity(event.eventId(), Instant.now()));
    }
}

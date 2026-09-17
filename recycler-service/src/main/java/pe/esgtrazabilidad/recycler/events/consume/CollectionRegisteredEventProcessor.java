package pe.esgtrazabilidad.recycler.events.consume;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.events.ledger.CollectionRegisteredLedgerEntity;
import pe.esgtrazabilidad.recycler.events.ledger.CollectionRegisteredLedgerJpaRepository;

/**
 * A separate Spring bean from the listener, not a private method on it:
 * {@code @Transactional} only takes effect through Spring's proxy, which
 * self-invocation bypasses entirely.
 *
 * <p>The ledger insert is deliberately LAST, flushed explicitly, inside the
 * same transaction as the increment -- not a try/catch around an early
 * ledger insert followed by the increment. A duplicate-key violation here
 * fails the flush, which rolls back this entire transaction (the increment
 * included) and rethrows to the caller. That's simpler and more portable
 * than it sounds: the alternative (insert-first, catch-and-continue) needs
 * Propagation.NESTED (a real DB savepoint) to keep the transaction alive
 * after catching the violation -- not supported by Spring's
 * JpaTransactionManager/Hibernate's default JpaDialect, confirmed by an
 * IT test that failed with NestedTransactionNotSupportedException before
 * this was reordered. Doing the idempotency check last avoids needing
 * savepoints at all: either everything in this method commits, or none of
 * it does.
 */
@Component
class CollectionRegisteredEventProcessor {

    private final AssociationRepository associationRepository;
    private final CollectionRegisteredLedgerJpaRepository ledgerRepository;

    CollectionRegisteredEventProcessor(
            AssociationRepository associationRepository, CollectionRegisteredLedgerJpaRepository ledgerRepository) {
        this.associationRepository = associationRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    void process(CollectionRegisteredEvent event) {
        associationRepository.incrementTotalKilos(event.associationId(), event.weightKg());
        ledgerRepository.saveAndFlush(new CollectionRegisteredLedgerEntity(event.eventId(), Instant.now()));
    }
}

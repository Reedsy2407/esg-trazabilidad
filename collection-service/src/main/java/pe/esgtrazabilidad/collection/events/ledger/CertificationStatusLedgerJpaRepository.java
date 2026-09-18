package pe.esgtrazabilidad.collection.events.ledger;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Public (unlike the outbox package's JpaRepository, which stays
 * package-private behind OutboxRepository): CertificationStatusEventListener
 * (Task 36, in the sibling events.consume package) uses this directly -- a
 * port/adapter split would be pure ceremony here, since there's no domain
 * concept beyond "have we seen this event_id" and no other consumer. Mirrors
 * recycler-service's CollectionRegisteredLedgerJpaRepository (Task 29).
 */
public interface CertificationStatusLedgerJpaRepository
        extends JpaRepository<CertificationStatusLedgerEntity, UUID> {
}

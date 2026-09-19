package pe.esgtrazabilidad.reporting.events.ledger;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Public (unlike a port/adapter split): there's no domain concept beyond
 * "have we seen this event_id" plus the period-scoped sum query
 * CertificateService (Phase 22) needs -- same reasoning as
 * recycler-service's CollectionRegisteredLedgerJpaRepository.
 */
public interface TracedCollectionEntryJpaRepository extends JpaRepository<TracedCollectionEntryEntity, UUID> {

    List<TracedCollectionEntryEntity> findByAssociationIdAndCollectionDateBetween(
            UUID associationId, LocalDate periodStart, LocalDate periodEnd);
}

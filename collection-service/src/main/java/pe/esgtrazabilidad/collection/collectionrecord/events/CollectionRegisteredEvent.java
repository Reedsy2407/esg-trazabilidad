package pe.esgtrazabilidad.collection.collectionrecord.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.kernel.events.DomainEvent;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * routingKey() is a plain override, not a record component -- verified
 * empirically (CollectionRecordApiIT.creatingARecordWritesAPendingOutboxEntry
 * parses the real serialized JSON and asserts its exact key set) that Jackson
 * 2.x serializes a record by its canonical components only, so it does NOT
 * leak into the wire payload as an 8th field. Same reasoning applies to every
 * other DomainEvent record (CertificationExpiredEvent, CertificationRenewedEvent,
 * etc.) -- no need to re-verify per event, but if a future event needs a
 * genuinely derived (non-component) method for some other purpose, confirm it
 * doesn't leak the same way before assuming it's safe.
 */
public record CollectionRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID recordId,
        UUID neighborId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg,
        String districtUbigeo)
        implements DomainEvent {

    /** Without a district: what a consumer sees for events published before v3 (D3). */
    public static CollectionRegisteredEvent from(CollectionRecord record) {
        return from(record, null);
    }

    /**
     * {@code districtUbigeo} is the neighbour's ubigeo at the moment of the record (6 digits,
     * nullable). It stays fixed: a later change of the neighbour's district doesn't rewrite it.
     */
    public static CollectionRegisteredEvent from(CollectionRecord record, String districtUbigeo) {
        return new CollectionRegisteredEvent(
                IdGenerator.generate(),
                Instant.now(),
                record.getId(),
                record.getNeighborId(),
                record.getAssociationId(),
                record.getCollectionDate(),
                record.getWeightKg(),
                districtUbigeo);
    }

    @Override
    public String routingKey() {
        return "collection.record.registered";
    }
}

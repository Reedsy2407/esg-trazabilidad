package pe.esgtrazabilidad.collection.collectionrecord.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.collection.collectionrecord.domain.CollectionRecord;
import pe.esgtrazabilidad.kernel.events.DomainEvent;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

public record CollectionRegisteredEvent(
        UUID eventId,
        Instant occurredAt,
        UUID recordId,
        UUID neighborId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg)
        implements DomainEvent {

    public static CollectionRegisteredEvent from(CollectionRecord record) {
        return new CollectionRegisteredEvent(
                IdGenerator.generate(),
                Instant.now(),
                record.getId(),
                record.getNeighborId(),
                record.getAssociationId(),
                record.getCollectionDate(),
                record.getWeightKg());
    }

    @Override
    public String routingKey() {
        return "collection.record.registered";
    }
}

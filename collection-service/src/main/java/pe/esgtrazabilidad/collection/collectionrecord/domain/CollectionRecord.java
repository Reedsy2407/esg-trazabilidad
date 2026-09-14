package pe.esgtrazabilidad.collection.collectionrecord.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * An immutable historical fact: once a collection happened, it happened.
 * No status field, no lifecycle transitions, no update path -- create,
 * get, and list only.
 */
public class CollectionRecord {

    private final UUID id;
    private final UUID neighborId;
    private final UUID scheduleId;
    private final UUID associationId;
    private final LocalDate collectionDate;
    private final BigDecimal weightKg;

    private CollectionRecord(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg) {
        this.id = id;
        this.neighborId = neighborId;
        this.scheduleId = scheduleId;
        this.associationId = associationId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
    }

    public static CollectionRecord create(
            UUID neighborId, UUID scheduleId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
        if (collectionDate == null) {
            throw new IllegalArgumentException("La fecha de recojo es obligatoria");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("El peso recolectado debe ser mayor que cero");
        }
        return new CollectionRecord(
                IdGenerator.generate(), neighborId, scheduleId, associationId, collectionDate, weightKg);
    }

    public static CollectionRecord reconstruct(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg) {
        return new CollectionRecord(id, neighborId, scheduleId, associationId, collectionDate, weightKg);
    }

    public UUID getId() {
        return id;
    }

    public UUID getNeighborId() {
        return neighborId;
    }

    public UUID getScheduleId() {
        return scheduleId;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public LocalDate getCollectionDate() {
        return collectionDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }
}

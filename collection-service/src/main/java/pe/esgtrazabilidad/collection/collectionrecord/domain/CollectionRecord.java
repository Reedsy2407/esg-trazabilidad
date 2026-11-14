package pe.esgtrazabilidad.collection.collectionrecord.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * An immutable historical fact: once a collection happened, it happened.
 * No status field, no lifecycle transitions, no update path -- create,
 * get, and list only.
 *
 * <p>{@code recyclerId} and {@code wasteType} are optional: they exist only for records created
 * by a check-in of the field app; the records staff created before (and still creates from the
 * web) carry neither.
 */
public class CollectionRecord {

    private final UUID id;
    private final UUID neighborId;
    private final UUID scheduleId;
    private final UUID associationId;
    private final LocalDate collectionDate;
    private final BigDecimal weightKg;
    private final UUID recyclerId;
    private final String wasteType;

    private CollectionRecord(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg,
            UUID recyclerId,
            String wasteType) {
        this.id = id;
        this.neighborId = neighborId;
        this.scheduleId = scheduleId;
        this.associationId = associationId;
        this.collectionDate = collectionDate;
        this.weightKg = weightKg;
        this.recyclerId = recyclerId;
        this.wasteType = wasteType;
    }

    public static CollectionRecord create(
            UUID neighborId, UUID scheduleId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
        return create(neighborId, scheduleId, associationId, collectionDate, weightKg, null, null);
    }

    public static CollectionRecord create(
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg,
            UUID recyclerId,
            String wasteType) {
        if (collectionDate == null) {
            throw new IllegalArgumentException("La fecha de recojo es obligatoria");
        }
        if (weightKg == null || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("El peso recolectado debe ser mayor que cero");
        }
        return new CollectionRecord(
                IdGenerator.generate(),
                neighborId,
                scheduleId,
                associationId,
                collectionDate,
                weightKg,
                recyclerId,
                wasteType);
    }

    public static CollectionRecord reconstruct(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg) {
        return reconstruct(id, neighborId, scheduleId, associationId, collectionDate, weightKg, null, null);
    }

    public static CollectionRecord reconstruct(
            UUID id,
            UUID neighborId,
            UUID scheduleId,
            UUID associationId,
            LocalDate collectionDate,
            BigDecimal weightKg,
            UUID recyclerId,
            String wasteType) {
        return new CollectionRecord(
                id, neighborId, scheduleId, associationId, collectionDate, weightKg, recyclerId, wasteType);
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

    /** The recycler who collected it, or null for a record staff created from the web. */
    public UUID getRecyclerId() {
        return recyclerId;
    }

    /** A code of the waste-type catalogue, or null for a record staff created from the web. */
    public String getWasteType() {
        return wasteType;
    }
}

package pe.esgtrazabilidad.collection.collectionrecord.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code recyclerId} and {@code wasteType} are set only by the field check-in (module 2); the
 * staff web form never sends them.
 */
public record CreateCollectionRecordCommand(
        UUID neighborId,
        UUID scheduleId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg,
        UUID recyclerId,
        String wasteType) {

    public CreateCollectionRecordCommand(
            UUID neighborId, UUID scheduleId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
        this(neighborId, scheduleId, associationId, collectionDate, weightKg, null, null);
    }
}

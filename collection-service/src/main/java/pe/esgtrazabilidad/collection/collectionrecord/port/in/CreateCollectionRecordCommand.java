package pe.esgtrazabilidad.collection.collectionrecord.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateCollectionRecordCommand(
        UUID neighborId, UUID scheduleId, UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
}

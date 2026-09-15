package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CollectionRecordResponse(
        UUID id,
        UUID neighborId,
        UUID scheduleId,
        UUID associationId,
        LocalDate collectionDate,
        BigDecimal weightKg) {
}

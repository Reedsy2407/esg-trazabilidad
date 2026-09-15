package pe.esgtrazabilidad.collection.collectionrecord.adapter.in.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateCollectionRecordRequest(
        UUID scheduleId,
        @NotNull UUID associationId,
        @NotNull LocalDate collectionDate,
        @NotNull @Positive BigDecimal weightKg) {
}

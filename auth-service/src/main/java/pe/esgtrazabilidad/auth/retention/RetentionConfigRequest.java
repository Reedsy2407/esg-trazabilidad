package pe.esgtrazabilidad.auth.retention;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Out of range or missing is a 400 VALIDATION_ERROR; the bounds are the same ones the service enforces. */
record RetentionConfigRequest(
        @NotNull @Min(RetentionService.MIN_GPS_DAYS) @Max(RetentionService.MAX_GPS_DAYS) Integer gpsDays,
        @NotNull @Min(RetentionService.MIN_PHOTO_MONTHS) @Max(RetentionService.MAX_PHOTO_MONTHS) Integer photoMonths) {
}

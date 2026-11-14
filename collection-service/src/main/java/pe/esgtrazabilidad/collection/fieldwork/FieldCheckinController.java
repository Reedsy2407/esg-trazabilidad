package pe.esgtrazabilidad.collection.fieldwork;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/**
 * Check-in of the recycler's device. Recycler and association come only from the token: the body
 * has no field for them. The weight takes at most 2 decimals (the record's own scale), so the
 * server never rounds silently and the content hash of a repeat stays the same.
 */
@RestController
class FieldCheckinController {

    private final FieldCheckinService service;

    FieldCheckinController(FieldCheckinService service) {
        this.service = service;
    }

    record CheckinRequest(
            @NotNull UUID clientId,
            @NotNull UUID neighborId,
            @NotNull UUID scheduleId,
            @NotNull Instant recordedAt,
            @DecimalMin("-90") @DecimalMax("90") Double lat,
            @DecimalMin("-180") @DecimalMax("180") Double lng,
            @DecimalMin("0") Double accuracyM,
            @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal weightKg,
            @NotBlank @Size(max = 32) String wasteType,
            @Size(max = 200) String photoKey,
            @NotNull @Min(0) Long deviceSeq) {

        /** lat, lng and accuracyM travel together or not at all (B2, correction 4). */
        @JsonIgnore
        @AssertTrue(message = "lat, lng y accuracyM deben enviarse juntos o ninguno")
        boolean isCoordinatesConsistent() {
            int present = (lat != null ? 1 : 0) + (lng != null ? 1 : 0) + (accuracyM != null ? 1 : 0);
            return present == 0 || present == 3;
        }
    }

    record CheckinResponse(UUID checkinId, UUID recordId) {
    }

    @PostMapping("/workdays/{workdayId}/checkins")
    ResponseEntity<CheckinResponse> checkin(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workdayId, @Valid @RequestBody CheckinRequest request) {
        RecyclerClaims claims = RecyclerClaims.from(jwt);
        FieldCheckinService.CheckinResult result = service.register(
                claims,
                workdayId,
                new FieldCheckinService.CheckinCommand(
                        request.clientId(),
                        request.neighborId(),
                        request.scheduleId(),
                        request.recordedAt(),
                        request.lat(),
                        request.lng(),
                        request.accuracyM(),
                        request.weightKg(),
                        request.wasteType(),
                        request.photoKey(),
                        request.deviceSeq()));
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new CheckinResponse(result.checkinId(), result.recordId()));
    }
}

package pe.esgtrazabilidad.collection.fieldwork;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/** Workday and GPS points of the recycler's device. Recycler and association only from the token. */
@RestController
class WorkdayController {

    /** Largest batch of points a request may carry (CA-34). */
    static final int MAX_POINTS_PER_BATCH = 500;

    private final WorkdayService service;

    WorkdayController(WorkdayService service) {
        this.service = service;
    }

    record OpenRequest(@NotNull Instant startedAt, @NotNull UUID clientId) {
    }

    record OpenResponse(UUID workdayId) {
    }

    record CloseRequest(@NotNull Instant endedAt) {
    }

    record PointRequest(
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double lat,
            @NotNull @DecimalMin("-180") @DecimalMax("180") Double lng,
            @NotNull @DecimalMin("0") Double accuracyM,
            @NotNull Instant recordedAt,
            @NotNull @Min(0) Long deviceSeq) {
    }

    record PointsRequest(@NotNull @Size(min = 1, max = MAX_POINTS_PER_BATCH) List<@NotNull @Valid PointRequest> points) {
    }

    @PostMapping("/workdays")
    ResponseEntity<OpenResponse> open(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody OpenRequest request) {
        RecyclerClaims claims = RecyclerClaims.from(jwt);
        WorkdayService.OpenResult result = service.open(
                claims.recyclerId(), claims.associationId(), claims.locationConsent(), request.startedAt(), request.clientId());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(new OpenResponse(result.workdayId()));
    }

    @PostMapping("/workdays/{workdayId}/points")
    WorkdayService.PointsResult points(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workdayId, @Valid @RequestBody PointsRequest request) {
        RecyclerClaims claims = RecyclerClaims.from(jwt);
        List<WorkdayService.PointInput> points = request.points().stream()
                .map(p -> new WorkdayService.PointInput(p.lat(), p.lng(), p.accuracyM(), p.recordedAt(), p.deviceSeq()))
                .toList();
        return service.addPoints(claims.recyclerId(), workdayId, points);
    }

    @PostMapping("/workdays/{workdayId}/close")
    WorkdayService.CloseResult close(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID workdayId, @Valid @RequestBody CloseRequest request) {
        RecyclerClaims claims = RecyclerClaims.from(jwt);
        return service.close(claims.recyclerId(), workdayId, request.endedAt());
    }
}

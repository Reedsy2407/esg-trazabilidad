package pe.esgtrazabilidad.collection.fieldwork;

import java.time.Instant;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff reads of the field work (A.5c). STAFF and ADMIN only: {@code /recyclers/**} falls under the chain's
 * default rule and {@code GET /workdays/{id}} has its own matcher, so a RECYCLER token gets 403 AUTH-000.
 */
@RestController
class StaffWorkdayController {

    private static final int MAX_PAGE_SIZE = 100;

    private final StaffWorkdayService service;

    StaffWorkdayController(StaffWorkdayService service) {
        this.service = service;
    }

    @GetMapping("/recyclers/{recyclerId}/workdays")
    StaffWorkdayService.WorkdaySummaryPage list(
            @PathVariable UUID recyclerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(recyclerId, from, to, Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
    }

    @GetMapping("/workdays/{workdayId}")
    StaffWorkdayService.WorkdayDetail detail(
            @PathVariable UUID workdayId,
            @RequestParam(defaultValue = "0") long pointsOffset,
            @RequestParam(defaultValue = "5000") int pointsLimit) {
        return service.detail(
                workdayId,
                Math.max(pointsOffset, 0),
                Math.min(Math.max(pointsLimit, 1), StaffWorkdayService.MAX_POINTS_LIMIT));
    }
}

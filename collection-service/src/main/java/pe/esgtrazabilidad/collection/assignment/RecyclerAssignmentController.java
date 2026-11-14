package pe.esgtrazabilidad.collection.assignment;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Staff-only (STAFF and ADMIN; a RECYCLER token gets 403 AUTH-000 from the chain's default rule). */
@RestController
class RecyclerAssignmentController {

    private final RecyclerAssignmentService service;

    RecyclerAssignmentController(RecyclerAssignmentService service) {
        this.service = service;
    }

    record ReplaceRequest(@NotNull List<@NotNull UUID> scheduleIds) {
    }

    record BulkRequest(@NotNull UUID recyclerId, @NotNull @Pattern(regexp = "\\d{6}") String districtUbigeo) {
    }

    @PutMapping("/recyclers/{recyclerId}/assignment")
    RecyclerAssignmentService.ReplaceResult replace(
            @PathVariable UUID recyclerId, @Valid @RequestBody ReplaceRequest request) {
        return service.replace(recyclerId, request.scheduleIds());
    }

    @GetMapping("/recyclers/{recyclerId}/assignment")
    AssignmentView get(@PathVariable UUID recyclerId) {
        return new AssignmentView(recyclerId, service.list(recyclerId));
    }

    @PostMapping("/assignments/bulk")
    RecyclerAssignmentService.BulkResult bulk(@Valid @RequestBody BulkRequest request) {
        return service.bulkAssign(request.recyclerId(), request.districtUbigeo());
    }

    record AssignmentView(UUID recyclerId, List<RecyclerAssignmentService.AssignedSchedule> schedules) {
    }
}

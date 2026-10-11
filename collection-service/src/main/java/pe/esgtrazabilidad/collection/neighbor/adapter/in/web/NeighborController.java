package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.in.ChangeNeighborStatusUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.EditNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.GetNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.ListNeighborsUseCase;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.kernel.web.PageResponse;

@RestController
@RequestMapping("/neighbors")
class NeighborController {

    private final CreateNeighborUseCase createNeighborUseCase;
    private final GetNeighborUseCase getNeighborUseCase;
    private final ListNeighborsUseCase listNeighborsUseCase;
    private final EditNeighborUseCase editNeighborUseCase;
    private final ChangeNeighborStatusUseCase changeNeighborStatusUseCase;
    private final NeighborMapper mapper;

    NeighborController(
            CreateNeighborUseCase createNeighborUseCase,
            GetNeighborUseCase getNeighborUseCase,
            ListNeighborsUseCase listNeighborsUseCase,
            EditNeighborUseCase editNeighborUseCase,
            ChangeNeighborStatusUseCase changeNeighborStatusUseCase,
            NeighborMapper mapper) {
        this.createNeighborUseCase = createNeighborUseCase;
        this.getNeighborUseCase = getNeighborUseCase;
        this.listNeighborsUseCase = listNeighborsUseCase;
        this.editNeighborUseCase = editNeighborUseCase;
        this.changeNeighborStatusUseCase = changeNeighborStatusUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<NeighborResponse> create(@Valid @RequestBody CreateNeighborRequest request) {
        Neighbor neighbor = createNeighborUseCase.create(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(neighbor));
    }

    @PatchMapping("/{id}")
    NeighborResponse edit(@PathVariable UUID id, @Valid @RequestBody EditNeighborRequest request) {
        return mapper.toResponse(editNeighborUseCase.edit(mapper.toCommand(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    NeighborResponse deactivate(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeNeighborStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(changeNeighborStatusUseCase.deactivate(id, request.reason(), CurrentActor.from(jwt)));
    }

    @PatchMapping("/{id}/activate")
    NeighborResponse activate(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeNeighborStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(changeNeighborStatusUseCase.activate(id, request.reason(), CurrentActor.from(jwt)));
    }

    @GetMapping("/{id}/status-changes")
    List<NeighborStatusChangeResponse> statusChanges(@PathVariable UUID id) {
        return changeNeighborStatusUseCase.history(id).stream().map(mapper::toResponse).toList();
    }

    @GetMapping("/{id}")
    NeighborResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getNeighborUseCase.getById(id));
    }

    @GetMapping
    PageResponse<NeighborResponse> list(
            @RequestParam(required = false) NeighborStatus status,
            @RequestParam(required = false) String districtUbigeo,
            @RequestParam(required = false) String district,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<NeighborResponse> page =
                listNeighborsUseCase.list(status, districtUbigeo, district, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

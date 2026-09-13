package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.recycler.PageResponse;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.recycler.recycler.port.in.ActivateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.DeactivateRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.GetRecyclerUseCase;
import pe.esgtrazabilidad.recycler.recycler.port.in.ListRecyclersUseCase;

@RestController
@RequestMapping("/associations/{associationId}/recyclers")
class RecyclerController {

    private final CreateRecyclerUseCase createRecyclerUseCase;
    private final GetRecyclerUseCase getRecyclerUseCase;
    private final ListRecyclersUseCase listRecyclersUseCase;
    private final ActivateRecyclerUseCase activateRecyclerUseCase;
    private final DeactivateRecyclerUseCase deactivateRecyclerUseCase;
    private final RecyclerMapper mapper;

    RecyclerController(
            CreateRecyclerUseCase createRecyclerUseCase,
            GetRecyclerUseCase getRecyclerUseCase,
            ListRecyclersUseCase listRecyclersUseCase,
            ActivateRecyclerUseCase activateRecyclerUseCase,
            DeactivateRecyclerUseCase deactivateRecyclerUseCase,
            RecyclerMapper mapper) {
        this.createRecyclerUseCase = createRecyclerUseCase;
        this.getRecyclerUseCase = getRecyclerUseCase;
        this.listRecyclersUseCase = listRecyclersUseCase;
        this.activateRecyclerUseCase = activateRecyclerUseCase;
        this.deactivateRecyclerUseCase = deactivateRecyclerUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<RecyclerResponse> create(
            @PathVariable UUID associationId, @Valid @RequestBody CreateRecyclerRequest request) {
        Recycler recycler = createRecyclerUseCase.create(mapper.toCommand(associationId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(recycler));
    }

    @GetMapping("/{id}")
    RecyclerResponse getById(@PathVariable UUID associationId, @PathVariable UUID id) {
        return mapper.toResponse(getRecyclerUseCase.getById(associationId, id));
    }

    @PatchMapping("/{id}/activate")
    RecyclerResponse activate(@PathVariable UUID associationId, @PathVariable UUID id) {
        return mapper.toResponse(activateRecyclerUseCase.activate(associationId, id));
    }

    @PatchMapping("/{id}/deactivate")
    RecyclerResponse deactivate(@PathVariable UUID associationId, @PathVariable UUID id) {
        return mapper.toResponse(deactivateRecyclerUseCase.deactivate(associationId, id));
    }

    @GetMapping
    PageResponse<RecyclerResponse> list(
            @PathVariable UUID associationId,
            @RequestParam(required = false) RecyclerStatus status,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<RecyclerResponse> page =
                listRecyclersUseCase.list(associationId, status, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

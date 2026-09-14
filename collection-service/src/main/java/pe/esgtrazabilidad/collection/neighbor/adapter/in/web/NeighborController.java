package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.GetNeighborUseCase;
import pe.esgtrazabilidad.collection.neighbor.port.in.ListNeighborsUseCase;
import pe.esgtrazabilidad.kernel.web.PageResponse;

@RestController
@RequestMapping("/neighbors")
class NeighborController {

    private final CreateNeighborUseCase createNeighborUseCase;
    private final GetNeighborUseCase getNeighborUseCase;
    private final ListNeighborsUseCase listNeighborsUseCase;
    private final NeighborMapper mapper;

    NeighborController(
            CreateNeighborUseCase createNeighborUseCase,
            GetNeighborUseCase getNeighborUseCase,
            ListNeighborsUseCase listNeighborsUseCase,
            NeighborMapper mapper) {
        this.createNeighborUseCase = createNeighborUseCase;
        this.getNeighborUseCase = getNeighborUseCase;
        this.listNeighborsUseCase = listNeighborsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<NeighborResponse> create(@Valid @RequestBody CreateNeighborRequest request) {
        Neighbor neighbor = createNeighborUseCase.create(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(neighbor));
    }

    @GetMapping("/{id}")
    NeighborResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getNeighborUseCase.getById(id));
    }

    @GetMapping
    PageResponse<NeighborResponse> list(
            @RequestParam(required = false) NeighborStatus status,
            @RequestParam(required = false) String district,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<NeighborResponse> page =
                listNeighborsUseCase.list(status, district, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

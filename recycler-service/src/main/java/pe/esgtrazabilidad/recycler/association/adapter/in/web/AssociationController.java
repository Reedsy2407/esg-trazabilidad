package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.recycler.PageResponse;
import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;
import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.GetAssociationUseCase;
import pe.esgtrazabilidad.recycler.association.port.in.ListAssociationsUseCase;

@RestController
@RequestMapping("/associations")
class AssociationController {

    private final CreateAssociationUseCase createAssociationUseCase;
    private final GetAssociationUseCase getAssociationUseCase;
    private final ListAssociationsUseCase listAssociationsUseCase;
    private final AssociationMapper mapper;

    AssociationController(
            CreateAssociationUseCase createAssociationUseCase,
            GetAssociationUseCase getAssociationUseCase,
            ListAssociationsUseCase listAssociationsUseCase,
            AssociationMapper mapper) {
        this.createAssociationUseCase = createAssociationUseCase;
        this.getAssociationUseCase = getAssociationUseCase;
        this.listAssociationsUseCase = listAssociationsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    ResponseEntity<AssociationResponse> create(@Valid @RequestBody CreateAssociationRequest request) {
        Association association = createAssociationUseCase.create(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(association));
    }

    @GetMapping("/{id}")
    AssociationResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getAssociationUseCase.getById(id));
    }

    @GetMapping
    PageResponse<AssociationResponse> list(
            @RequestParam(required = false) AssociationStatus status, Pageable pageable) {
        Page<AssociationResponse> page = listAssociationsUseCase.list(status, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

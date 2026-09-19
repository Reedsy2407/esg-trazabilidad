package pe.esgtrazabilidad.reporting.sigersolsync.adapter.in.web;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.kernel.web.PageResponse;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolSync;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.GetSigersolSyncUseCase;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.ListSigersolSyncsUseCase;
import pe.esgtrazabilidad.reporting.sigersolsync.port.in.RegisterSigersolSyncUseCase;

@RestController
@RequestMapping("/sigersol-syncs")
class SigersolSyncController {

    private final RegisterSigersolSyncUseCase registerSigersolSyncUseCase;
    private final GetSigersolSyncUseCase getSigersolSyncUseCase;
    private final ListSigersolSyncsUseCase listSigersolSyncsUseCase;
    private final SigersolSyncMapper mapper;

    SigersolSyncController(
            RegisterSigersolSyncUseCase registerSigersolSyncUseCase,
            GetSigersolSyncUseCase getSigersolSyncUseCase,
            ListSigersolSyncsUseCase listSigersolSyncsUseCase,
            SigersolSyncMapper mapper) {
        this.registerSigersolSyncUseCase = registerSigersolSyncUseCase;
        this.getSigersolSyncUseCase = getSigersolSyncUseCase;
        this.listSigersolSyncsUseCase = listSigersolSyncsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<SigersolSyncResponse> register(@Valid @RequestBody RegisterSigersolSyncRequest request) {
        SigersolSync sigersolSync = registerSigersolSyncUseCase.register(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(sigersolSync));
    }

    @GetMapping("/{id}")
    SigersolSyncResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getSigersolSyncUseCase.getById(id));
    }

    @GetMapping
    PageResponse<SigersolSyncResponse> list(
            @PageableDefault(size = 20, sort = "periodStart", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<SigersolSyncResponse> page = listSigersolSyncsUseCase.list(pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

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
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.GetTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ListTrackedCompaniesUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyUseCase;

@RestController
@RequestMapping("/tracked-companies")
class TrackedCompanyController {

    private final RegisterTrackedCompanyUseCase registerTrackedCompanyUseCase;
    private final GetTrackedCompanyUseCase getTrackedCompanyUseCase;
    private final ListTrackedCompaniesUseCase listTrackedCompaniesUseCase;
    private final TrackedCompanyMapper mapper;

    TrackedCompanyController(
            RegisterTrackedCompanyUseCase registerTrackedCompanyUseCase,
            GetTrackedCompanyUseCase getTrackedCompanyUseCase,
            ListTrackedCompaniesUseCase listTrackedCompaniesUseCase,
            TrackedCompanyMapper mapper) {
        this.registerTrackedCompanyUseCase = registerTrackedCompanyUseCase;
        this.getTrackedCompanyUseCase = getTrackedCompanyUseCase;
        this.listTrackedCompaniesUseCase = listTrackedCompaniesUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<TrackedCompanyResponse> register(@Valid @RequestBody RegisterTrackedCompanyRequest request) {
        TrackedCompany trackedCompany = registerTrackedCompanyUseCase.register(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(trackedCompany));
    }

    @GetMapping("/{id}")
    TrackedCompanyResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getTrackedCompanyUseCase.getById(id));
    }

    @GetMapping
    PageResponse<TrackedCompanyResponse> list(
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<TrackedCompanyResponse> page = listTrackedCompaniesUseCase.list(pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.kernel.web.PageResponse;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.GetTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ListTrackedCompaniesUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.ManageTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RefreshTrackedCompanySunatUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyUseCase;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterUnverifiedTrackedCompanyUseCase;

@RestController
@RequestMapping("/tracked-companies")
class TrackedCompanyController {

    private final RegisterTrackedCompanyUseCase registerTrackedCompanyUseCase;
    private final RegisterUnverifiedTrackedCompanyUseCase registerUnverifiedUseCase;
    private final RefreshTrackedCompanySunatUseCase refreshSunatUseCase;
    private final ManageTrackedCompanyUseCase manageUseCase;
    private final GetTrackedCompanyUseCase getTrackedCompanyUseCase;
    private final ListTrackedCompaniesUseCase listTrackedCompaniesUseCase;
    private final TrackedCompanyMapper mapper;

    TrackedCompanyController(
            RegisterTrackedCompanyUseCase registerTrackedCompanyUseCase,
            RegisterUnverifiedTrackedCompanyUseCase registerUnverifiedUseCase,
            RefreshTrackedCompanySunatUseCase refreshSunatUseCase,
            ManageTrackedCompanyUseCase manageUseCase,
            GetTrackedCompanyUseCase getTrackedCompanyUseCase,
            ListTrackedCompaniesUseCase listTrackedCompaniesUseCase,
            TrackedCompanyMapper mapper) {
        this.registerTrackedCompanyUseCase = registerTrackedCompanyUseCase;
        this.registerUnverifiedUseCase = registerUnverifiedUseCase;
        this.refreshSunatUseCase = refreshSunatUseCase;
        this.manageUseCase = manageUseCase;
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

    /** Only when SUNAT does not answer: the server repeats the lookup and refuses (RPT-014) if it does. */
    @PostMapping("/unverified")
    ResponseEntity<TrackedCompanyResponse> registerUnverified(
            @Valid @RequestBody RegisterUnverifiedTrackedCompanyRequest request) {
        TrackedCompany trackedCompany = registerUnverifiedUseCase.registerUnverified(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(trackedCompany));
    }

    @PostMapping("/{id}/sunat-refresh")
    TrackedCompanyResponse refreshFromSunat(@PathVariable UUID id) {
        return mapper.toResponse(refreshSunatUseCase.refreshFromSunat(id));
    }

    @PatchMapping("/{id}")
    TrackedCompanyResponse edit(@PathVariable UUID id, @Valid @RequestBody EditTrackedCompanyRequest request) {
        return mapper.toResponse(manageUseCase.edit(mapper.toCommand(id, request)));
    }

    @PatchMapping("/{id}/deactivate")
    TrackedCompanyResponse deactivate(
            @PathVariable UUID id, @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(manageUseCase.deactivate(id, request.reason(), CurrentActor.from(jwt)));
    }

    @PatchMapping("/{id}/activate")
    TrackedCompanyResponse activate(
            @PathVariable UUID id, @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponse(manageUseCase.activate(id, request.reason(), CurrentActor.from(jwt)));
    }

    /** Logical: the company disappears from reads and frees its RUC. RPT-017 if it has any certificate. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID id, @Valid @RequestBody ReasonRequest request, @AuthenticationPrincipal Jwt jwt) {
        manageUseCase.delete(id, request.reason(), CurrentActor.from(jwt));
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

package pe.esgtrazabilidad.collection.company.adapter.in.web;

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

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;
import pe.esgtrazabilidad.collection.company.port.in.CreateCompanyUseCase;
import pe.esgtrazabilidad.collection.company.port.in.GetCompanyUseCase;
import pe.esgtrazabilidad.collection.company.port.in.ListCompaniesUseCase;
import pe.esgtrazabilidad.kernel.web.PageResponse;

@RestController
@RequestMapping("/companies")
class CompanyController {

    private final CreateCompanyUseCase createCompanyUseCase;
    private final GetCompanyUseCase getCompanyUseCase;
    private final ListCompaniesUseCase listCompaniesUseCase;
    private final CompanyMapper mapper;

    CompanyController(
            CreateCompanyUseCase createCompanyUseCase,
            GetCompanyUseCase getCompanyUseCase,
            ListCompaniesUseCase listCompaniesUseCase,
            CompanyMapper mapper) {
        this.createCompanyUseCase = createCompanyUseCase;
        this.getCompanyUseCase = getCompanyUseCase;
        this.listCompaniesUseCase = listCompaniesUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
        Company company = createCompanyUseCase.create(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(company));
    }

    @GetMapping("/{id}")
    CompanyResponse getById(@PathVariable UUID id) {
        return mapper.toResponse(getCompanyUseCase.getById(id));
    }

    @GetMapping
    PageResponse<CompanyResponse> list(
            @RequestParam(required = false) CompanyStatus status,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<CompanyResponse> page = listCompaniesUseCase.list(status, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

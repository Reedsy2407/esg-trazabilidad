package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

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
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.recycler.PageResponse;
import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.GetCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.ListCertificationsUseCase;

@RestController
@RequestMapping("/associations/{associationId}/certifications")
class CertificationController {

    private final CreateCertificationUseCase createCertificationUseCase;
    private final GetCertificationUseCase getCertificationUseCase;
    private final ListCertificationsUseCase listCertificationsUseCase;
    private final CertificationMapper mapper;

    CertificationController(
            CreateCertificationUseCase createCertificationUseCase,
            GetCertificationUseCase getCertificationUseCase,
            ListCertificationsUseCase listCertificationsUseCase,
            CertificationMapper mapper) {
        this.createCertificationUseCase = createCertificationUseCase;
        this.getCertificationUseCase = getCertificationUseCase;
        this.listCertificationsUseCase = listCertificationsUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    ResponseEntity<CertificationResponse> create(
            @PathVariable UUID associationId, @Valid @RequestBody CreateCertificationRequest request) {
        Certification certification = createCertificationUseCase.create(mapper.toCommand(associationId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(certification));
    }

    @GetMapping("/{id}")
    CertificationResponse getById(@PathVariable UUID associationId, @PathVariable UUID id) {
        return mapper.toResponse(getCertificationUseCase.getById(associationId, id));
    }

    @GetMapping
    PageResponse<CertificationResponse> list(
            @PathVariable UUID associationId,
            @PageableDefault(size = 20, sort = "expirationDate", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<CertificationResponse> page =
                listCertificationsUseCase.list(associationId, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }
}

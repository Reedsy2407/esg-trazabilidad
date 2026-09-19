package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.kernel.web.PageResponse;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.port.in.ExportCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.GetCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.IssueCertificateUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.ListCertificatesUseCase;
import pe.esgtrazabilidad.reporting.certificate.port.in.PreviewCertificateSummaryUseCase;

@RestController
@RequestMapping("/tracked-companies/{companyId}")
class CertificateController {

    private final PreviewCertificateSummaryUseCase previewCertificateSummaryUseCase;
    private final IssueCertificateUseCase issueCertificateUseCase;
    private final GetCertificateUseCase getCertificateUseCase;
    private final ListCertificatesUseCase listCertificatesUseCase;
    private final ExportCertificateUseCase exportCertificateUseCase;
    private final CertificateMapper mapper;

    CertificateController(
            PreviewCertificateSummaryUseCase previewCertificateSummaryUseCase,
            IssueCertificateUseCase issueCertificateUseCase,
            GetCertificateUseCase getCertificateUseCase,
            ListCertificatesUseCase listCertificatesUseCase,
            ExportCertificateUseCase exportCertificateUseCase,
            CertificateMapper mapper) {
        this.previewCertificateSummaryUseCase = previewCertificateSummaryUseCase;
        this.issueCertificateUseCase = issueCertificateUseCase;
        this.getCertificateUseCase = getCertificateUseCase;
        this.listCertificatesUseCase = listCertificatesUseCase;
        this.exportCertificateUseCase = exportCertificateUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/certificate-summary")
    CertificateSummaryResponse previewSummary(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return mapper.toResponse(previewCertificateSummaryUseCase.previewSummary(companyId, periodStart, periodEnd));
    }

    @PostMapping("/certificates")
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<EsgCertificateResponse> issue(
            @PathVariable UUID companyId, @Valid @RequestBody IssueCertificateRequest request) {
        EsgCertificate certificate = issueCertificateUseCase.issue(mapper.toCommand(companyId, request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(certificate));
    }

    @GetMapping("/certificates/{id}")
    EsgCertificateResponse getById(@PathVariable UUID companyId, @PathVariable UUID id) {
        return mapper.toResponse(getCertificateUseCase.getById(companyId, id));
    }

    @GetMapping("/certificates")
    PageResponse<EsgCertificateResponse> list(
            @PathVariable UUID companyId,
            @PageableDefault(size = 20, sort = "issuedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<EsgCertificateResponse> page = listCertificatesUseCase.list(companyId, pageable).map(mapper::toResponse);
        return PageResponse.from(page);
    }

    @GetMapping("/certificates/{id}/pdf")
    ResponseEntity<byte[]> exportPdf(@PathVariable UUID companyId, @PathVariable UUID id) {
        byte[] pdfBytes = exportCertificateUseCase.exportPdf(companyId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("certificado-" + id + ".pdf"))
                .body(pdfBytes);
    }

    @GetMapping("/certificates/{id}/csv")
    ResponseEntity<byte[]> exportCsv(@PathVariable UUID companyId, @PathVariable UUID id) {
        byte[] csvBytes = exportCertificateUseCase.exportCsv(companyId, id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION, attachment("certificado-" + id + ".csv"))
                .body(csvBytes);
    }

    private static String attachment(String filename) {
        return ContentDisposition.attachment().filename(filename).build().toString();
    }
}

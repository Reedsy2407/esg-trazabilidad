package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.reporting.certificate.port.in.PreviewCertificateSummaryUseCase;

@RestController
@RequestMapping("/tracked-companies/{companyId}")
class CertificateController {

    private final PreviewCertificateSummaryUseCase previewCertificateSummaryUseCase;
    private final CertificateMapper mapper;

    CertificateController(PreviewCertificateSummaryUseCase previewCertificateSummaryUseCase, CertificateMapper mapper) {
        this.previewCertificateSummaryUseCase = previewCertificateSummaryUseCase;
        this.mapper = mapper;
    }

    @GetMapping("/certificate-summary")
    CertificateSummaryResponse previewSummary(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd) {
        return mapper.toResponse(previewCertificateSummaryUseCase.previewSummary(companyId, periodStart, periodEnd));
    }
}

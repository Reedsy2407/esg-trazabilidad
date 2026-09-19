package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

import static org.assertj.core.api.Assertions.assertThat;

class CertificatePdfExporterTest {

    private final CertificatePdfExporter exporter = new CertificatePdfExporter();

    @Test
    void producesAValidPdfWithTheCertificatesDataExtractable() throws IOException {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa de prueba SAC", "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"));

        byte[] pdfBytes = exporter.export(certificate);

        assertThat(pdfBytes).isNotEmpty();

        String extractedText;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            extractedText = new PDFTextStripper().getText(document);
        }

        assertThat(extractedText).contains("Empresa de prueba SAC");
        assertThat(extractedText).contains("20123456789");
        assertThat(extractedText).contains("2026-01-01");
        assertThat(extractedText).contains("2026-01-31");
        assertThat(extractedText).contains("120.50");
        assertThat(extractedText).contains("85.00");
    }
}

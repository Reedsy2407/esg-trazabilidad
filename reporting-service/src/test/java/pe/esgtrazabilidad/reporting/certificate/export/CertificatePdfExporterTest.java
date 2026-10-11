package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus;
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
        assertThat(extractedText).contains("Proyecto académico, sin validez legal");
        assertThat(extractedText).doesNotContain("ANULADO");
    }

    private EsgCertificate annulled(String reason, String email) {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa de prueba SAC", "20123456789", UUID.randomUUID());
        return EsgCertificate.reconstruct(
                UUID.randomUUID(),
                trackedCompany.getId(),
                trackedCompany.getAssociationId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"),
                Instant.parse("2026-02-01T00:00:00Z"),
                EsgCertificateStatus.ANNULLED,
                Instant.parse("2026-03-01T10:00:00Z"),
                UUID.fromString("01a0d8cb-f219-72a2-b006-5150f0e0d685"),
                email,
                reason);
    }

    private String textOf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void anAnnulledCertificatePrintsAnuladoWithWhenWhoWhyAndStillItsFrozenNumbers() throws IOException {
        String text = textOf(exporter.export(annulled("Datos erróneos del vecino", "ana@example.com")));

        assertThat(text).contains("ANULADO");
        assertThat(text).contains("2026-03-01T10:00:00Z");
        assertThat(text).contains("ana@example.com");
        assertThat(text).contains("Datos erróneos del vecino");
        assertThat(text).contains("120.50");
        assertThat(text).contains("Proyecto académico, sin validez legal");
    }

    @Test
    void anAnnulledCertificateWithoutAnEmailFallsBackToTheStaffUserId() throws IOException {
        String text = textOf(exporter.export(annulled("Motivo", null)));

        assertThat(text).contains("01a0d8cb-f219-72a2-b006-5150f0e0d685");
    }

    @Test
    void aLongReasonWithLineBreaksAndCharactersTheFontCannotEncodeStillExportsAndWraps() throws IOException {
        String reason = ("Error de captura con saltos\ny emoji \uD83D\uDE00 " + "palabra ".repeat(60)).substring(0, 500);

        String text = textOf(exporter.export(annulled(reason, "ana@example.com")));

        assertThat(text).contains("Error de captura con saltos y emoji ?");
        assertThat(text).contains("ANULADO");
    }

    @Test
    void aReasonWithOneUnbrokenRunLongerThanALineIsCutNotLost() throws IOException {
        String text = textOf(exporter.export(annulled("x".repeat(500), "ana@a.pe")));

        // No other text of the document contains an "x".
        assertThat(text.replaceAll("[^x]", "")).hasSize(500);
    }
}

package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

/**
 * Pure function: in-memory EsgCertificate -> PDF bytes. No Postgres/RabbitMQ
 * dependency, same discipline as CertificateCsvExporter (Task 55).
 */
@Component
public class CertificatePdfExporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final float MARGIN = 60f;
    private static final float LEADING = 22f;

    public byte[] export(EsgCertificate certificate) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float y = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                y = writeLine(content, titleFont, 16, MARGIN, y, "Certificado ESG de Trazabilidad");
                y -= LEADING / 2;

                y = writeLine(content, bodyFont, 11, MARGIN, y, "Empresa: " + certificate.getCompanyName());
                y = writeLine(content, bodyFont, 11, MARGIN, y, "RUC: " + certificate.getCompanyRuc());
                y = writeLine(
                        content,
                        bodyFont,
                        11,
                        MARGIN,
                        y,
                        "Periodo: " + certificate.getPeriodStart().format(DATE_FORMAT) + " a "
                                + certificate.getPeriodEnd().format(DATE_FORMAT));
                y = writeLine(
                        content,
                        bodyFont,
                        11,
                        MARGIN,
                        y,
                        "Kilos trazados: " + certificate.getKilosTrazados().toPlainString() + " kg");
                y = writeLine(
                        content,
                        bodyFont,
                        11,
                        MARGIN,
                        y,
                        "Cumplimiento de jerarquía: "
                                + certificate.getHierarchyCompliancePercent().toPlainString() + " %");
                writeLine(
                        content,
                        bodyFont,
                        11,
                        MARGIN,
                        y,
                        "Emitido: " + DateTimeFormatter.ISO_INSTANT.format(certificate.getIssuedAt()));
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
        }
    }

    private float writeLine(PDPageContentStream content, PDFont font, float fontSize, float x, float y, String text)
            throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text);
        content.endText();
        return y - LEADING;
    }
}

package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
 *
 * Every page carries the "academic project" footer; an annulled certificate
 * prints ANULADO prominently, with when, who and why, and still shows its
 * frozen numbers.
 */
@Component
public class CertificatePdfExporter {

    static final String FOOTER = "Proyecto académico, sin validez legal";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final float MARGIN = 60f;
    private static final float LEADING = 22f;
    private static final float FOOTER_Y = 30f;
    // Annulment reasons run up to 500 characters: wrap them inside the margins.
    private static final float WRAP_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    public byte[] export(EsgCertificate certificate) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            float y = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                y = writeLine(content, titleFont, 16, MARGIN, y, "Certificado ESG de Trazabilidad");
                if (certificate.isAnnulled()) {
                    y = writeLine(content, titleFont, 40, MARGIN, y - 18, "ANULADO") - 18;
                }
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
                y = writeLine(
                        content,
                        bodyFont,
                        11,
                        MARGIN,
                        y,
                        "Emitido: " + DateTimeFormatter.ISO_INSTANT.format(certificate.getIssuedAt()));

                if (certificate.isAnnulled()) {
                    y -= LEADING / 2;
                    y = writeLine(
                            content,
                            titleFont,
                            11,
                            MARGIN,
                            y,
                            "Anulado: " + DateTimeFormatter.ISO_INSTANT.format(certificate.getAnnulledAt()));
                    y = writeLine(
                            content,
                            bodyFont,
                            11,
                            MARGIN,
                            y,
                            "Anulado por: " + CertificateCsvExporter.annulledBy(certificate));
                    for (String line : wrap(bodyFont, 11, "Motivo: " + certificate.getAnnulmentReason())) {
                        y = writeLine(content, bodyFont, 11, MARGIN, y, line);
                    }
                }

                writeLine(content, bodyFont, 9, MARGIN, FOOTER_Y, FOOTER);
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
        content.showText(printable(font, text));
        content.endText();
        return y - LEADING;
    }

    /**
     * The standard fonts only cover WinAnsi and showText throws on anything
     * else: user-typed text (a reason, a name) must never fail the export, so
     * line breaks become spaces and unencodable characters become '?'.
     */
    private static String printable(PDFont font, String text) {
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint) || Character.isISOControl(codePoint)) {
                out.append(' ');
                return;
            }
            try {
                font.encode(new String(Character.toChars(codePoint)));
                out.appendCodePoint(codePoint);
            } catch (IOException | IllegalArgumentException e) {
                out.append('?');
            }
        });
        return out.toString();
    }

    private static List<String> wrap(PDFont font, float fontSize, String text) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : printable(font, text).split(" +")) {
            String remaining = word;
            while (!remaining.isEmpty()) {
                String candidate = current.isEmpty() ? remaining : current + " " + remaining;
                if (width(font, fontSize, candidate) <= WRAP_WIDTH) {
                    current = candidate;
                    remaining = "";
                } else if (!current.isEmpty()) {
                    lines.add(current);
                    current = "";
                } else {
                    // A single unbroken run longer than a line is cut where it overflows.
                    int cut = remaining.length();
                    while (cut > 1 && width(font, fontSize, remaining.substring(0, cut)) > WRAP_WIDTH) {
                        cut--;
                    }
                    lines.add(remaining.substring(0, cut));
                    remaining = remaining.substring(cut);
                }
            }
        }
        if (!current.isEmpty()) {
            lines.add(current);
        }
        return lines;
    }

    private static float width(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }
}

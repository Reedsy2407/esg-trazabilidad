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

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

/**
 * Pure function: in-memory EsgCertificate (plus the district context frozen at issue) -> PDF
 * bytes. No Postgres/RabbitMQ dependency, same discipline as CertificateCsvExporter (Task 55).
 *
 * Every page carries the "academic project" footer; an annulled certificate prints ANULADO
 * prominently, with when, who and why, and still shows its frozen numbers. The district section
 * lists SIGERSOL Municipal's aggregated counts with their source and year, and never says the
 * system validated anything against SIGERSOL; with many districts it continues on further pages.
 */
@Component
public class CertificatePdfExporter {

    static final String FOOTER = "Proyecto académico, sin validez legal";
    static final String DISTRICT_TITLE = "Contexto distrital (SIGERSOL Municipal)";
    static final String DISTRICT_NOTE =
            "Cifras públicas agregadas por distrito. Describen el distrito, no a la empresa ni a sus proveedores.";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final float MARGIN = 60f;
    private static final float LEADING = 22f;
    private static final float FOOTER_Y = 30f;
    /** Below this height a new page starts, leaving room above the footer. */
    private static final float BOTTOM_LIMIT = 70f;
    // Annulment reasons run up to 500 characters: wrap them inside the margins.
    private static final float WRAP_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;

    public byte[] export(EsgCertificate certificate) {
        return export(certificate, List.of());
    }

    public byte[] export(EsgCertificate certificate, List<DistrictLine> districtLines) {
        try (PDDocument document = new PDDocument()) {
            PDFont titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (Writer out = new Writer(document, bodyFont)) {
                out.line(titleFont, 16, "Certificado ESG de Trazabilidad");
                if (certificate.isAnnulled()) {
                    out.gap(18);
                    out.line(titleFont, 40, "ANULADO");
                    out.gap(18);
                }
                out.gap(LEADING / 2);

                out.line(bodyFont, 11, "Empresa: " + certificate.getCompanyName());
                out.line(bodyFont, 11, "RUC: " + certificate.getCompanyRuc());
                out.line(
                        bodyFont,
                        11,
                        "Periodo: " + certificate.getPeriodStart().format(DATE_FORMAT) + " a "
                                + certificate.getPeriodEnd().format(DATE_FORMAT));
                out.line(bodyFont, 11, "Kilos trazados: " + certificate.getKilosTrazados().toPlainString() + " kg");
                out.line(
                        bodyFont,
                        11,
                        "Cumplimiento de jerarquía: " + certificate.getHierarchyCompliancePercent().toPlainString()
                                + " %");
                out.line(bodyFont, 11, "Emitido: " + DateTimeFormatter.ISO_INSTANT.format(certificate.getIssuedAt()));

                if (certificate.isAnnulled()) {
                    out.gap(LEADING / 2);
                    out.line(
                            titleFont,
                            11,
                            "Anulado: " + DateTimeFormatter.ISO_INSTANT.format(certificate.getAnnulledAt()));
                    out.line(bodyFont, 11, "Anulado por: " + CertificateCsvExporter.annulledBy(certificate));
                    for (String line : wrap(bodyFont, 11, "Motivo: " + certificate.getAnnulmentReason())) {
                        out.line(bodyFont, 11, line);
                    }
                }

                if (!districtLines.isEmpty()) {
                    out.gap(LEADING / 2);
                    out.line(titleFont, 11, DISTRICT_TITLE);
                    for (String line : wrap(bodyFont, 9, DISTRICT_NOTE)) {
                        out.line(bodyFont, 9, line, 16f);
                    }
                    for (DistrictLine district : districtLines) {
                        writeDistrict(out, bodyFont, district);
                    }
                }
            }

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
        }
    }

    private void writeDistrict(Writer out, PDFont bodyFont, DistrictLine district) throws IOException {
        for (String line : wrap(bodyFont, 10, district.districtName() + ": " + district.kilos().toPlainString() + " kg trazados")) {
            out.line(bodyFont, 10, line, 15f);
        }
        String detail = switch (district.status()) {
            case AVAILABLE -> "Asociaciones: " + district.associationCount() + "; recicladores: " + district.recyclerCount()
                    + ". Fuente: SIGERSOL Municipal, año " + district.year() + ", datos agregados.";
            case UNAVAILABLE -> "Contexto de SIGERSOL Municipal no disponible.";
            case UNKNOWN_DISTRICT -> null;
        };
        if (detail != null) {
            for (String line : wrap(bodyFont, 9, "   " + detail)) {
                out.line(bodyFont, 9, line, 15f);
            }
        }
    }

    /** Writes lines top to bottom; starts a new page near the bottom and puts the footer on each. */
    private static final class Writer implements AutoCloseable {
        private final PDDocument document;
        private final PDFont footerFont;
        private PDPageContentStream content;
        private float y;

        Writer(PDDocument document, PDFont footerFont) throws IOException {
            this.document = document;
            this.footerFont = footerFont;
            newPage();
        }

        private void newPage() throws IOException {
            if (content != null) {
                finishPage();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            y = page.getMediaBox().getHeight() - MARGIN;
        }

        private void finishPage() throws IOException {
            text(footerFont, 9, MARGIN, FOOTER_Y, FOOTER);
            content.close();
        }

        void gap(float amount) {
            y -= amount;
        }

        void line(PDFont font, float fontSize, String text) throws IOException {
            line(font, fontSize, text, LEADING);
        }

        void line(PDFont font, float fontSize, String text, float leading) throws IOException {
            if (y < BOTTOM_LIMIT) {
                newPage();
            }
            text(font, fontSize, MARGIN, y, text);
            y -= leading;
        }

        private void text(PDFont font, float fontSize, float x, float atY, String text) throws IOException {
            content.beginText();
            content.setFont(font, fontSize);
            content.newLineAtOffset(x, atY);
            content.showText(printable(font, text));
            content.endText();
        }

        @Override
        public void close() throws IOException {
            finishPage();
        }
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

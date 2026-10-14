package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;

/**
 * Pure function: in-memory EsgCertificate (plus the district context frozen at issue) -> PDF
 * bytes. No Postgres/RabbitMQ dependency, same discipline as CertificateCsvExporter.
 *
 * Laid out like the ticket on screen: a title band, a VIGENTE / ANULADO stamp, the company, the
 * kilos as the one big figure and the rest as a striped table. Dates read dd/MM/yyyy and times are
 * Lima time. An annulled certificate says so three ways (stamp, boxed when/who/why, a pale diagonal
 * mark on every page) and still shows its frozen numbers. The district section lists SIGERSOL
 * Municipal's aggregated counts with their source and year, and never says the system validated
 * anything against SIGERSOL; with many districts it continues on further pages. Every page carries
 * the "academic project" footer and its page number.
 */
@Component
public class CertificatePdfExporter {

    static final String FOOTER = "Proyecto académico, sin validez legal";
    static final String DISTRICT_TITLE = "Contexto distrital (SIGERSOL Municipal)";
    static final String DISTRICT_NOTE =
            "Cifras públicas agregadas por distrito. Describen el distrito, no a la empresa ni a sus proveedores.";

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final float PAGE_W = PDRectangle.A4.getWidth();
    private static final float PAGE_H = PDRectangle.A4.getHeight();
    private static final float MARGIN = 50f;
    private static final float CONTENT_W = PAGE_W - 2 * MARGIN;
    private static final float BAND_H = 78f;
    /** Below this height a new page starts, leaving room above the footer. */
    private static final float BOTTOM_LIMIT = 80f;
    private static final float VALUE_X = 235f;
    private static final float VALUE_W = PAGE_W - MARGIN - VALUE_X;

    private static final float[] INK = {0.10f, 0.10f, 0.10f};
    private static final float[] SOFT = {0.42f, 0.42f, 0.40f};
    private static final float[] BAND = {0.09f, 0.20f, 0.16f};
    private static final float[] VALID = {0.09f, 0.45f, 0.25f};
    private static final float[] VOID = {0.70f, 0.10f, 0.10f};
    private static final float[] VOID_TINT = {1.00f, 0.93f, 0.93f};
    private static final float[] VOID_MARK = {1.00f, 0.88f, 0.88f};
    private static final float[] STRIPE = {0.95f, 0.95f, 0.93f};
    private static final float[] RULE = {0.78f, 0.78f, 0.75f};

    public byte[] export(EsgCertificate certificate) {
        return export(certificate, List.of());
    }

    public byte[] export(EsgCertificate certificate, List<DistrictLine> districtLines) {
        try (PDDocument document = new PDDocument()) {
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont mono = new PDType1Font(Standard14Fonts.FontName.COURIER);

            try (Page page = new Page(document, regular, bold, certificate)) {
                writeHeader(page, certificate);
                writeCompany(page, certificate);
                writeFigures(page, certificate, mono);
                if (!districtLines.isEmpty()) {
                    writeDistricts(page, districtLines);
                }
            }
            stampFooters(document, regular);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            document.save(bytes);
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PDF del certificado", e);
        }
    }

    // ---- sections -----------------------------------------------------------------------------

    private void writeHeader(Page page, EsgCertificate certificate) throws IOException {
        boolean annulled = certificate.isAnnulled();
        float[] stampColor = annulled ? VOID : VALID;

        // Stamp: the state first, in a frame, with the issue time beside it.
        page.strokeRect(MARGIN, page.y - 30, 132, 30, stampColor, 2f);
        page.centered(page.bold, 15, stampColor, MARGIN + 66, page.y - 21, annulled ? "ANULADO" : "VIGENTE");
        page.text(page.regular, 10, SOFT, MARGIN + 148, page.y - 20, "Emitido el " + dateTime(certificate.getIssuedAt()));
        page.y -= 30 + 24;

        if (annulled) {
            String by = "Anulado el " + dateTime(certificate.getAnnulledAt()) + " por " + CertificateCsvExporter.annulledBy(certificate);
            List<String> byLines = wrap(page.regular, 10, by, CONTENT_W - 24);
            List<String> reasonLines = wrap(page.regular, 10, "Motivo: " + certificate.getAnnulmentReason(), CONTENT_W - 24);
            float boxH = 20 + (byLines.size() + reasonLines.size()) * 14f;
            page.ensureSpace(boxH + 20);
            page.fillRect(MARGIN, page.y - boxH, CONTENT_W, boxH, VOID_TINT);
            page.strokeRect(MARGIN, page.y - boxH, CONTENT_W, boxH, VOID, 1f);
            float ty = page.y - 18;
            for (String line : byLines) {
                page.text(page.bold, 10, VOID, MARGIN + 12, ty, line);
                ty -= 14;
            }
            for (String line : reasonLines) {
                page.text(page.regular, 10, INK, MARGIN + 12, ty, line);
                ty -= 14;
            }
            page.y -= boxH + 22;
        }
    }

    private void writeCompany(Page page, EsgCertificate certificate) throws IOException {
        page.ensureSpace(90);
        page.text(page.bold, 8, SOFT, MARGIN, page.y, "EMPRESA");
        page.y -= 8;
        // The name wraps inside the margins; a long business name never runs off the page edge.
        for (String line : wrap(page.bold, 18, certificate.getCompanyName(), CONTENT_W)) {
            page.ensureSpace(30);
            page.y -= 22;
            page.text(page.bold, 18, INK, MARGIN, page.y, line);
        }
        page.y -= 18;
        page.text(page.regular, 11, INK, MARGIN, page.y, "RUC " + certificate.getCompanyRuc());
        page.y -= 16;
        page.rule(RULE);
        page.y -= 26;
    }

    private void writeFigures(Page page, EsgCertificate certificate, PDFont mono) throws IOException {
        page.ensureSpace(150);
        page.text(page.bold, 8, SOFT, MARGIN, page.y, "KILOS TRAZADOS");
        page.y -= 34;
        page.text(page.bold, 32, INK, MARGIN, page.y, kg(certificate.getKilosTrazados()));
        page.y -= 30;

        row(page, 0, "Cumplimiento de jerarquía", percent(certificate.getHierarchyCompliancePercent()), page.regular, 11);
        row(page, 1, "Período", certificate.getPeriodStart().format(DATE_FORMAT) + " al " + certificate.getPeriodEnd().format(DATE_FORMAT), page.regular, 11);
        row(page, 2, "Emitido (hora de Lima)", dateTime(certificate.getIssuedAt()), page.regular, 11);
        row(page, 3, "Código del certificado", String.valueOf(certificate.getId()), mono, 10);
        page.y -= 4;
        for (String line : wrap(page.regular, 8.5f, "El cumplimiento de jerarquía es un dato SIGERSOL de ingreso manual.", CONTENT_W)) {
            page.text(page.regular, 8.5f, SOFT, MARGIN, page.y, line);
            page.y -= 12;
        }
        page.y -= 14;
    }

    private void writeDistricts(Page page, List<DistrictLine> districts) throws IOException {
        page.ensureSpace(90);
        page.text(page.bold, 12, INK, MARGIN, page.y, DISTRICT_TITLE);
        page.y -= 16;
        for (String line : wrap(page.regular, 8.5f, DISTRICT_NOTE, CONTENT_W)) {
            page.text(page.regular, 8.5f, SOFT, MARGIN, page.y, line);
            page.y -= 12;
        }
        page.y -= 8;

        float contextX = 345f;
        float contextW = PAGE_W - MARGIN - contextX;
        tableHead(page, contextX);
        int index = 0;
        for (DistrictLine district : districts) {
            List<String> nameLines = wrap(page.regular, 10, district.districtName(), contextX - MARGIN - 110);
            List<String> contextLines = wrap(page.regular, 8.5f, districtContext(district), contextW);
            float rowH = Math.max(nameLines.size() * 13f, contextLines.size() * 11.5f) + 10;
            if (page.y - rowH < BOTTOM_LIMIT) {
                page.newPage();
                tableHead(page, contextX);
            }
            if (index % 2 == 0) {
                page.fillRect(MARGIN, page.y - rowH + 4, CONTENT_W, rowH, STRIPE);
            }
            float ny = page.y - 8;
            for (String line : nameLines) {
                page.text(page.regular, 10, INK, MARGIN + 6, ny, line);
                ny -= 13;
            }
            page.right(page.regular, 10, INK, contextX - 16, page.y - 8, kg(district.kilos()));
            float cy = page.y - 8;
            for (String line : contextLines) {
                page.text(page.regular, 8.5f, SOFT, contextX, cy, line);
                cy -= 11.5f;
            }
            page.y -= rowH;
            index++;
        }
    }

    private void tableHead(Page page, float contextX) throws IOException {
        page.text(page.bold, 8, SOFT, MARGIN + 6, page.y, "DISTRITO");
        page.right(page.bold, 8, SOFT, contextX - 16, page.y, "KILOS");
        page.text(page.bold, 8, SOFT, contextX, page.y, "CONTEXTO SIGERSOL MUNICIPAL");
        page.y -= 6;
        page.rule(RULE);
        page.y -= 10;
    }

    private static String districtContext(DistrictLine district) {
        return switch (district.status()) {
            case AVAILABLE -> "Asociaciones: " + district.associationCount() + "; recicladores: " + district.recyclerCount()
                    + ". Fuente: SIGERSOL Municipal, año " + district.year() + ", datos agregados.";
            case UNAVAILABLE -> "Contexto de SIGERSOL Municipal no disponible.";
            case UNKNOWN_DISTRICT -> "Recojos sin distrito registrado.";
        };
    }

    /** A striped label/value line of the figures table; the value wraps inside the right margin. */
    private void row(Page page, int index, String label, String value, PDFont valueFont, float size) throws IOException {
        List<String> lines = wrap(valueFont, size, value, VALUE_W);
        float rowH = lines.size() * 15f + 10;
        page.ensureSpace(rowH + 4);
        if (index % 2 == 0) {
            page.fillRect(MARGIN, page.y - rowH + 4, CONTENT_W, rowH, STRIPE);
        }
        page.text(page.regular, 10, SOFT, MARGIN + 8, page.y - 10, label);
        float ty = page.y - 10;
        for (String line : lines) {
            page.text(valueFont, size, INK, VALUE_X, ty, line);
            ty -= 15;
        }
        page.y -= rowH;
    }

    /** Footer and "Página i de n" on every page, once the page count is known. */
    private void stampFooters(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, document.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.setStrokingColor(RULE[0], RULE[1], RULE[2]);
                cs.setLineWidth(0.7f);
                cs.moveTo(MARGIN, 52);
                cs.lineTo(PAGE_W - MARGIN, 52);
                cs.stroke();
                cs.setNonStrokingColor(SOFT[0], SOFT[1], SOFT[2]);
                cs.beginText();
                cs.setFont(font, 9);
                cs.newLineAtOffset(MARGIN, 38);
                cs.showText(printable(font, FOOTER));
                cs.endText();
                String pageText = "Página " + (i + 1) + " de " + total;
                cs.beginText();
                cs.setFont(font, 9);
                cs.newLineAtOffset(PAGE_W - MARGIN - width(font, 9, pageText), 38);
                cs.showText(printable(font, pageText));
                cs.endText();
            }
        }
    }

    // ---- formatting ---------------------------------------------------------------------------

    private static String dateTime(Instant instant) {
        return DATE_TIME_FORMAT.format(instant.atZone(LIMA));
    }

    private static String kg(BigDecimal value) {
        return new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value) + " kg";
    }

    private static String percent(BigDecimal value) {
        return new DecimalFormat("0.00", DecimalFormatSymbols.getInstance(Locale.US)).format(value) + " %";
    }

    // ---- drawing ------------------------------------------------------------------------------

    /** Draws pages top to bottom, starts a new one near the bottom, repeats the band and the ANULADO mark. */
    private static final class Page implements AutoCloseable {
        private final PDDocument document;
        final PDFont regular;
        final PDFont bold;
        private final boolean annulled;
        private final String company;
        private PDPageContentStream content;
        float y;
        private boolean first = true;

        Page(PDDocument document, PDFont regular, PDFont bold, EsgCertificate certificate) throws IOException {
            this.document = document;
            this.regular = regular;
            this.bold = bold;
            this.annulled = certificate.isAnnulled();
            this.company = certificate.getCompanyName();
            newPage();
        }

        void newPage() throws IOException {
            if (content != null) {
                content.close();
            }
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            content = new PDPageContentStream(document, page);
            if (annulled) {
                content.saveGraphicsState();
                content.setNonStrokingColor(VOID_MARK[0], VOID_MARK[1], VOID_MARK[2]);
                content.beginText();
                content.setFont(bold, 100);
                content.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(38), 110, 250));
                content.showText("ANULADO");
                content.endText();
                content.restoreGraphicsState();
            }
            fillRect(0, PAGE_H - BAND_H, PAGE_W, BAND_H, BAND);
            if (first) {
                text(bold, 20, new float[] {1, 1, 1}, MARGIN, PAGE_H - 40, "Certificado de trazabilidad ESG");
                text(regular, 9, new float[] {0.80f, 0.86f, 0.83f}, MARGIN, PAGE_H - 60, "Plataforma de trazabilidad ESG · Lima Metropolitana");
                first = false;
                y = PAGE_H - BAND_H - 30;
            } else {
                text(bold, 12, new float[] {1, 1, 1}, MARGIN, PAGE_H - 36, "Certificado de trazabilidad ESG (continuación)");
                String name = printable(regular, company);
                text(regular, 9, new float[] {0.80f, 0.86f, 0.83f}, MARGIN, PAGE_H - 56, name.length() > 90 ? name.substring(0, 90) + "…" : name);
                y = PAGE_H - BAND_H - 30;
            }
        }

        void ensureSpace(float needed) throws IOException {
            if (y - needed < BOTTOM_LIMIT) {
                newPage();
            }
        }

        void fillRect(float x, float yy, float w, float h, float[] color) throws IOException {
            content.setNonStrokingColor(color[0], color[1], color[2]);
            content.addRect(x, yy, w, h);
            content.fill();
        }

        void strokeRect(float x, float yy, float w, float h, float[] color, float lineWidth) throws IOException {
            content.setStrokingColor(color[0], color[1], color[2]);
            content.setLineWidth(lineWidth);
            content.addRect(x, yy, w, h);
            content.stroke();
        }

        void rule(float[] color) throws IOException {
            content.setStrokingColor(color[0], color[1], color[2]);
            content.setLineWidth(0.8f);
            content.moveTo(MARGIN, y);
            content.lineTo(PAGE_W - MARGIN, y);
            content.stroke();
        }

        void text(PDFont font, float size, float[] color, float x, float atY, String text) throws IOException {
            content.setNonStrokingColor(color[0], color[1], color[2]);
            content.beginText();
            content.setFont(font, size);
            content.newLineAtOffset(x, atY);
            content.showText(printable(font, text));
            content.endText();
        }

        void right(PDFont font, float size, float[] color, float rightEdge, float atY, String text) throws IOException {
            text(font, size, color, rightEdge - width(font, size, printable(font, text)), atY, text);
        }

        void centered(PDFont font, float size, float[] color, float centerX, float atY, String text) throws IOException {
            text(font, size, color, centerX - width(font, size, printable(font, text)) / 2, atY, text);
        }

        @Override
        public void close() throws IOException {
            content.close();
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

    private static List<String> wrap(PDFont font, float fontSize, String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        String current = "";
        for (String word : printable(font, text).split(" +")) {
            String remaining = word;
            while (!remaining.isEmpty()) {
                String candidate = current.isEmpty() ? remaining : current + " " + remaining;
                if (width(font, fontSize, candidate) <= maxWidth) {
                    current = candidate;
                    remaining = "";
                } else if (!current.isEmpty()) {
                    lines.add(current);
                    current = "";
                } else {
                    // A single unbroken run longer than a line is cut where it overflows.
                    int cut = remaining.length();
                    while (cut > 1 && width(font, fontSize, remaining.substring(0, cut)) > maxWidth) {
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
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static float width(PDFont font, float fontSize, String text) throws IOException {
        return font.getStringWidth(text) / 1000 * fontSize;
    }
}

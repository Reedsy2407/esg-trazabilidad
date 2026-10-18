package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.certificate.HierarchyDeclaration;
import pe.esgtrazabilidad.reporting.sigersolsync.domain.SigersolOrigin;
import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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
        assertThat(extractedText).contains("01/01/2026");
        assertThat(extractedText).contains("31/01/2026");
        assertThat(extractedText).contains("120.50");
        assertThat(extractedText).contains("85.00");
        assertThat(extractedText).contains("Proyecto académico, sin validez legal");
        assertThat(extractedText).contains("VIGENTE").doesNotContain("ANULADO");
    }

    // ---- the brand header: the logo's blue and the logo mark -----------------------------------

    @Test
    void theHeaderBandIsTheLogosBlueWithTheLogoAndTheTwoTitleLines() throws IOException {
        byte[] pdf = exporter.export(issued());

        try (PDDocument document = Loader.loadPDF(pdf)) {
            List<Object> tokens = tokensOf(document.getPage(0));
            // #1A576C (--brand in the frontend's design tokens), the exact value, as the band's fill.
            assertThat(fillColors(tokens)).anySatisfy(c -> assertThat(c).containsExactly(new float[] {26 / 255f, 87 / 255f, 108 / 255f}, within(0.001f)));
            // The logo is the mark drawn as vector paths: the leaf alone takes bezier curves (halo, fill, vein).
            assertThat(operatorCount(tokens, "c")).isGreaterThanOrEqualTo(7);
            // White on the band: the mark and the title.
            assertThat(fillColors(tokens)).anySatisfy(c -> assertThat(c).containsExactly(new float[] {1f, 1f, 1f}, within(0.001f)));
        }
        String text = normalized(textOf(pdf));
        assertThat(text).contains("Certificado de trazabilidad ESG");
        assertThat(text).contains("Plataforma de trazabilidad ESG · Lima Metropolitana");
    }

    @Test
    void everyPageOfALongCertificateRepeatsTheBandWithTheLogo() throws IOException {
        List<DistrictLine> districts = IntStream.range(0, 40)
                .mapToObj(i -> new DistrictLine(
                        String.format("1501%02d", i), "Distrito " + i, BigDecimal.ONE, DistrictLine.Status.AVAILABLE, 2024, 1, 2))
                .toList();

        try (PDDocument document = Loader.loadPDF(exporter.export(issued(), districts))) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (PDPage page : document.getPages()) {
                List<Object> tokens = tokensOf(page);
                assertThat(operatorCount(tokens, "c")).as("the logo's curves on every page").isGreaterThanOrEqualTo(7);
                assertThat(fillColors(tokens)).anySatisfy(c -> assertThat(c).containsExactly(new float[] {26 / 255f, 87 / 255f, 108 / 255f}, within(0.001f)));
            }
        }
    }

    @Test
    void theBandsTextMeetsAaContrast() {
        // White on the brand blue is 8.0:1 (AAA); the band's soft text, --brand-soft, 5.65:1 (AA for small text).
        assertThat(contrast(CertificatePdfExporter.WHITE, CertificatePdfExporter.BAND)).isGreaterThanOrEqualTo(7.9);
        assertThat(contrast(CertificatePdfExporter.BAND_SOFT, CertificatePdfExporter.BAND)).isGreaterThanOrEqualTo(4.5);
    }

    @Test
    void theLogoKeepsItsProportionsAndItsClearSpaceInsideTheBand() {
        float height = CertificatePdfExporter.LOGO_H;
        float width = CertificateLogo.widthFor(height);
        // About 14 mm tall (40 pt) and a little narrower than tall, never stretched.
        assertThat(height / 2.835f).isBetween(13f, 15f);
        assertThat(width / height).isBetween(0.90f, 1.0f);
        // The leaf is about 0.39 of the mark's width: the clear space above and below must be at least that.
        float clearSpace = (CertificatePdfExporter.BAND_H - height) / 2f;
        assertThat(clearSpace).isGreaterThanOrEqualTo(0.39f * width);
    }

    @Test
    void theContinuationHeaderKeepsALongCapitalsNameInsideTheRightMargin() throws IOException {
        String name = "MMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMMM";
        List<DistrictLine> districts = IntStream.range(0, 40)
                .mapToObj(i -> new DistrictLine(
                        String.format("1501%02d", i), "Distrito " + i, BigDecimal.ONE, DistrictLine.Status.AVAILABLE, 2024, 1, 2))
                .toList();
        EsgCertificate certificate = EsgCertificate.issue(
                TrackedCompany.create(name, "20123456789", UUID.randomUUID()),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("120.50"), new BigDecimal("85.00"));

        try (PDDocument document = Loader.loadPDF(exporter.export(certificate, districts))) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) throws IOException {
                    for (TextPosition position : positions) {
                        assertThat(position.getEndX()).as("text ends inside the right margin").isLessThanOrEqualTo(595.3f - 50f + 0.5f);
                    }
                    super.writeString(text, positions);
                }
            };
            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("MMMM").contains("...");
        }
    }

    private static List<Object> tokensOf(PDPage page) throws IOException {
        return new PDFStreamParser(page).parse();
    }

    private static long operatorCount(List<Object> tokens, String name) {
        return tokens.stream().filter(t -> t instanceof Operator op && op.getName().equals(name)).count();
    }

    /** The RGB operands of every non-stroking colour operation (the rg operator) in the stream. */
    private static List<float[]> fillColors(List<Object> tokens) {
        List<float[]> colors = new ArrayList<>();
        for (int i = 3; i < tokens.size(); i++) {
            if (tokens.get(i) instanceof Operator op && op.getName().equals("rg")
                    && tokens.get(i - 1) instanceof COSNumber b && tokens.get(i - 2) instanceof COSNumber g
                    && tokens.get(i - 3) instanceof COSNumber r) {
                colors.add(new float[] {r.floatValue(), g.floatValue(), b.floatValue()});
            }
        }
        return colors;
    }

    private static double contrast(float[] a, float[] b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    }

    private static double luminance(float[] rgb) {
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            c[i] = rgb[i] <= 0.03928 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    private EsgCertificate issued() {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa de prueba SAC", "20123456789", UUID.randomUUID());
        return EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"));
    }

    @Test
    void theHierarchyFigureIsLabelledDeclaredAndTheNoteSaysWhoDeclaredItAndWithWhatProof() throws IOException {
        byte[] pdf = exporter.export(
                issued(),
                List.of(),
                new HierarchyDeclaration(SigersolOrigin.REFERENCED_PROOF, "ana@asociacion.pe", "Informe 2025 · envío 8841"));

        String text = normalized(textOf(pdf));

        assertThat(text).contains("Cumplimiento de jerarquía (declarado)");
        assertThat(text)
                .contains("El cumplimiento de jerarquía es un dato declarado por el operador de la asociación "
                        + "(origen: con comprobante referenciado, registrado por ana@asociacion.pe), "
                        + "respaldado por el comprobante «Informe 2025 · envío 8841»; "
                        + "no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.");
        assertThat(text).doesNotContain("dato SIGERSOL de ingreso manual");
    }

    @Test
    void withoutAProofOrAnAuthorTheNoteLeavesThoseParts() throws IOException {
        String declared = normalized(textOf(exporter.export(
                issued(), List.of(), new HierarchyDeclaration(SigersolOrigin.DECLARED_BY_OPERATOR, "ana@asociacion.pe", null))));
        assertThat(declared).contains("(origen: declarado por el operador, registrado por ana@asociacion.pe); no es una cifra oficial");
        assertThat(declared).doesNotContain("respaldado");

        String old = normalized(textOf(exporter.export(issued())));
        assertThat(old).contains("(origen: no registrado); no es una cifra oficial de SIGERSOL ni la verifica esta plataforma.");
    }

    @Test
    void theDistrictSectionNamesSigersolsAggregatedCountsWithTheirYearAndSource() throws IOException {
        byte[] pdf = exporter.export(issued(), List.of(
                new DistrictLine("150114", "La Molina", new BigDecimal("100.00"), DistrictLine.Status.AVAILABLE, 2024, 3, 31),
                new DistrictLine("150131", "San Isidro", new BigDecimal("20.50"), DistrictLine.Status.UNAVAILABLE, null, null, null),
                DistrictLine.unknown(new BigDecimal("0.00"))));

        String text = normalized(textOf(pdf));

        assertThat(text).contains("Contexto distrital (SIGERSOL Municipal)");
        assertThat(text).contains("La Molina").contains("100.00 kg");
        assertThat(text).contains("Asociaciones: 3; recicladores: 31");
        assertThat(text).contains("Fuente: SIGERSOL Municipal, año 2024, datos agregados");
        assertThat(text).contains("San Isidro").contains("20.50 kg");
        assertThat(text).contains("Contexto de SIGERSOL Municipal no disponible");
        assertThat(text).contains("Distrito desconocido").contains("0.00 kg");
        // The system never claims to validate anything against SIGERSOL.
        assertThat(text.toLowerCase()).doesNotContain("validad").doesNotContain("verificado por sigersol");
        assertThat(text).contains("Describen el distrito, no a la empresa ni a sus proveedores");
    }

    @Test
    void withoutDistrictsThereIsNoDistrictSection() throws IOException {
        assertThat(textOf(exporter.export(issued(), List.of()))).doesNotContain("Contexto distrital");
    }

    @Test
    void manyDistrictsContinueOnFurtherPagesAndEveryPageKeepsTheFooter() throws IOException {
        List<DistrictLine> districts = IntStream.range(0, 40)
                .mapToObj(i -> new DistrictLine(
                        String.format("1501%02d", i), "Distrito " + i, BigDecimal.ONE, DistrictLine.Status.AVAILABLE, 2024, 1, 2))
                .toList();

        byte[] pdf = exporter.export(issued(), districts);

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document)).contains("Proyecto académico, sin validez legal").contains("Página " + page + " de " + document.getNumberOfPages());
            }
            assertThat(new PDFTextStripper().getText(document)).contains("Distrito 39");
        }
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

    private static String normalized(String text) {
        return text.replaceAll("\\s+", " ");
    }

    private String textOf(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void anAnnulledCertificatePrintsAnuladoWithWhenWhoWhyAndStillItsFrozenNumbers() throws IOException {
        String text = normalized(textOf(exporter.export(annulled("Datos erróneos del vecino", "ana@example.com"))));

        assertThat(text).contains("ANULADO");
        // Lima time (UTC-5) as dd/MM/yyyy HH:mm, never the raw instant.
        assertThat(text).contains("01/03/2026 05:00").doesNotContain("T10:00:00Z");
        assertThat(text).contains("31/01/2026 19:00").doesNotContain("2026-02-01T00:00:00Z");
        assertThat(text).contains("ana@example.com");
        assertThat(text).contains("Datos erróneos del vecino");
        assertThat(text).contains("120.50");
        assertThat(text).contains("Proyecto académico, sin validez legal");
    }

    @Test
    void aLongCompanyNameWrapsInsideTheMarginsInsteadOfRunningOffThePage() throws IOException {
        String name = "Corporación Peruana de Reciclaje y Gestión Integral de Residuos Sólidos Urbanos del Cono Norte Sociedad Anónima Cerrada";
        TrackedCompany company = TrackedCompany.create(name, "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                company, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("1.00"), new BigDecimal("1.00"));

        byte[] pdf = exporter.export(certificate);

        assertThat(normalized(textOf(pdf))).contains("Corporación Peruana de Reciclaje").contains("Sociedad Anónima Cerrada");
        try (PDDocument document = Loader.loadPDF(pdf)) {
            // Every glyph stays inside the printable width (50 pt margins on a 595 pt page).
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text, List<TextPosition> positions) throws IOException {
                    for (TextPosition p : positions) {
                        assertThat(p.getXDirAdj() + p.getWidthDirAdj()).isLessThanOrEqualTo(595f - 49f);
                    }
                    super.writeString(text, positions);
                }
            };
            stripper.getText(document);
        }
    }

    @Test
    void anAnnulledCertificateWithoutAnEmailFallsBackToTheStaffUserId() throws IOException {
        String text = textOf(exporter.export(annulled("Motivo", null)));

        assertThat(text).contains("01a0d8cb-f219-72a2-b006-5150f0e0d685");
    }

    @Test
    void aLongReasonWithLineBreaksAndCharactersTheFontCannotEncodeStillExportsAndWraps() throws IOException {
        String reason = ("Error de captura con saltos\ny emoji \uD83D\uDE00 " + "palabra ".repeat(60)).substring(0, 500);

        String text = normalized(textOf(exporter.export(annulled(reason, "ana@example.com"))));

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

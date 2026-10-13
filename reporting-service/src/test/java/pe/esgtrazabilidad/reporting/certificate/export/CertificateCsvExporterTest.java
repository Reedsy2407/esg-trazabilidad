package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateCsvExporterTest {

    private final CertificateCsvExporter exporter = new CertificateCsvExporter();

    @Test
    void theDistrictTableCarriesSigersolsCountsWithTheirSourceAndAnInjectionSafeName() throws IOException {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa", "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("8.00"), new BigDecimal("85.00"));

        byte[] csv = exporter.export(certificate, List.of(), List.of(
                new DistrictLine("150114", "La Molina", new BigDecimal("5.00"), DistrictLine.Status.AVAILABLE, 2024, 3, 31),
                new DistrictLine("150131", "=SUMA(1)", new BigDecimal("2.00"), DistrictLine.Status.UNAVAILABLE, null, null, null),
                DistrictLine.unknown(new BigDecimal("1.00"))));

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(new String(csv, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            records = parser.getRecords();
        }
        assertThat(records).anySatisfy(r -> assertThat(r)
                .containsExactly("Distrito", "Ubigeo", "Kilos trazados", "Contexto", "Asociaciones", "Recicladores", "Fuente"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly(
                "La Molina", "150114", "5.00", "disponible", "3", "31", "SIGERSOL Municipal, año 2024, datos agregados"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly(
                "'=SUMA(1)", "150131", "2.00", "no disponible", "", "", ""));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly(
                "Distrito desconocido", "", "1.00", "distrito desconocido", "", "", ""));
    }

    @Test
    void withoutDistrictsTheCsvHasNoDistrictTable() throws IOException {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa", "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("8.00"), new BigDecimal("85.00"));

        assertThat(new String(exporter.export(certificate, List.of()), StandardCharsets.UTF_8)).doesNotContain("Ubigeo");
    }

    @Test
    void producesValidCsvWithSummaryAndLineItemsRoundTripping() throws IOException {
        TrackedCompany trackedCompany =
                TrackedCompany.create("Empresa, S.A.C.", "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"));
        List<EsgCertificateLineItem> lineItems = List.of(
                EsgCertificateLineItem.of(certificate.getId(), LocalDate.of(2026, 1, 5), new BigDecimal("50.00")),
                EsgCertificateLineItem.of(certificate.getId(), LocalDate.of(2026, 1, 20), new BigDecimal("70.50")));

        byte[] csvBytes = exporter.export(certificate, lineItems);

        assertThat(csvBytes).isNotEmpty();

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            records = parser.getRecords();
        }

        // Summary section: the comma inside the company name must round-trip
        // as a single field, proving Commons CSV escaped it on write.
        assertThat(records.get(0)).containsExactly("Empresa", "Empresa, S.A.C.");
        assertThat(records.get(1)).containsExactly("RUC", "20123456789");
        assertThat(records.get(4)).containsExactly("Kilos trazados", "120.50");
        assertThat(records.get(5)).containsExactly("Cumplimiento de jerarquia (%)", "85.00");

        // Line items table: every frozen entry must appear as its own row.
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("2026-01-05", "50.00"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("2026-01-20", "70.50"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+cmd", "-2", "@SUM(A1)", "\tEmpresa", "\rEmpresa"})
    void prefixesACompanyNameStartingWithAFormulaCharacterSoASpreadsheetReadsItAsText(String name)
            throws IOException {
        List<CSVRecord> records = export(name);

        assertThat(records.get(0)).containsExactly("Empresa", "'" + name);
        // Numbers are never prefixed: a leading "-" there is a real negative.
        assertThat(records.get(4)).containsExactly("Kilos trazados", "120.50");
        assertThat(records.get(5)).containsExactly("Cumplimiento de jerarquia (%)", "85.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Empresa SAC", "Empresa - Lima", "Empresa=1", "'ya citada"})
    void leavesACompanyNameThatDoesNotStartWithAFormulaCharacterAsTyped(String name) throws IOException {
        assertThat(export(name).get(0)).containsExactly("Empresa", name);
    }

    private List<CSVRecord> export(String companyName) throws IOException {
        TrackedCompany trackedCompany = TrackedCompany.create(companyName, "20123456789", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"));
        byte[] csvBytes = exporter.export(certificate, List.of());
        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            return parser.getRecords();
        }
    }

    @Test
    void producesAValidCsvWithNoLineItemsWhenNoneContributed() throws IOException {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa vacia SAC", "20999999999", UUID.randomUUID());
        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 2, 1),
                LocalDate.of(2026, 2, 28),
                BigDecimal.ZERO,
                BigDecimal.ZERO);

        byte[] csvBytes = exporter.export(certificate, List.of());

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            records = parser.getRecords();
        }

        assertThat(records.get(0)).containsExactly("Empresa", "Empresa vacia SAC");
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("Fecha de recoleccion", "Peso (kg)"));
    }

    private EsgCertificate annulled(String reason, String email) {
        TrackedCompany trackedCompany = TrackedCompany.create("Empresa SAC", "20123456789", UUID.randomUUID());
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

    private List<CSVRecord> parse(byte[] csvBytes) throws IOException {
        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            return parser.getRecords();
        }
    }

    @Test
    void anIssuedCertificateCarriesEstadoEmitidoAndNoAnnulmentRows() throws IOException {
        List<CSVRecord> records = export("Empresa SAC");

        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("estado", "Emitido"));
        assertThat(records).noneSatisfy(r -> assertThat(r.get(0)).isEqualTo("motivo_anulacion"));
    }

    @Test
    void anAnnulledCertificateCarriesEstadoAnuladoWithWhenWhoAndWhy() throws IOException {
        List<CSVRecord> records = parse(exporter.export(annulled("Datos erróneos, del vecino", "ana@example.com"), List.of()));

        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("estado", "Anulado"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("anulado_en", "2026-03-01T10:00:00Z"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("anulado_por", "ana@example.com"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("motivo_anulacion", "Datos erróneos, del vecino"));
        // The frozen numbers are still there.
        assertThat(records.get(4)).containsExactly("Kilos trazados", "120.50");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+cmd", "-2", "@SUM(A1)", "\tmotivo", "\rmotivo"})
    void anAnnulmentReasonStartingWithAFormulaCharacterIsPrefixedSoASpreadsheetReadsItAsText(String reason)
            throws IOException {
        List<CSVRecord> records = parse(exporter.export(annulled(reason, "ana@example.com"), List.of()));

        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("motivo_anulacion", "'" + reason));
    }

    @Test
    void anAnnulledCertificateWithoutAnEmailFallsBackToTheStaffUserId() throws IOException {
        List<CSVRecord> records = parse(exporter.export(annulled("Motivo", null), List.of()));

        assertThat(records)
                .anySatisfy(r -> assertThat(r).containsExactly("anulado_por", "01a0d8cb-f219-72a2-b006-5150f0e0d685"));
    }
}

package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateCsvExporterTest {

    private final CertificateCsvExporter exporter = new CertificateCsvExporter();

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
}

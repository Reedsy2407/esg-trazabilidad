package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificate;
import pe.esgtrazabilidad.reporting.certificate.domain.EsgCertificateLineItem;

/**
 * Pure function: an issued EsgCertificate plus its frozen line items -> CSV
 * bytes. No Postgres/RabbitMQ dependency, same discipline as
 * CertificatePdfExporter (Task 54).
 */
@Component
public class CertificateCsvExporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    public byte[] export(EsgCertificate certificate, List<EsgCertificateLineItem> lineItems) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord("Empresa", certificate.getCompanyName());
            printer.printRecord("RUC", certificate.getCompanyRuc());
            printer.printRecord("Periodo inicio", certificate.getPeriodStart().format(DATE_FORMAT));
            printer.printRecord("Periodo fin", certificate.getPeriodEnd().format(DATE_FORMAT));
            printer.printRecord("Kilos trazados", certificate.getKilosTrazados().toPlainString());
            printer.printRecord(
                    "Cumplimiento de jerarquia (%)", certificate.getHierarchyCompliancePercent().toPlainString());
            printer.printRecord("Emitido", DateTimeFormatter.ISO_INSTANT.format(certificate.getIssuedAt()));
            printer.println();

            printer.printRecord("Fecha de recoleccion", "Peso (kg)");
            for (EsgCertificateLineItem lineItem : lineItems) {
                printer.printRecord(
                        lineItem.getCollectionDate().format(DATE_FORMAT), lineItem.getWeightKg().toPlainString());
            }

            printer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el CSV del certificado", e);
        }
        return out.toByteArray();
    }
}

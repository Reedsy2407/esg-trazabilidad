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

import pe.esgtrazabilidad.reporting.certificate.domain.DistrictLine;
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

    /**
     * CSV formula injection (OWASP): a spreadsheet reads a cell starting with
     * one of these as a formula. Text fields typed by a user get a leading
     * single quote so they open as plain text. Never applied to numbers
     * (kilos, percentages), where a leading "-" is a real negative.
     */
    static String text(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return switch (value.charAt(0)) {
            case '=', '+', '-', '@', '\t', '\r' -> "'" + value;
            default -> value;
        };
    }

    /** The e-mail when the token carried one, the staff user id otherwise. */
    static String annulledBy(EsgCertificate certificate) {
        String email = certificate.getAnnulledByEmail();
        return email != null && !email.isBlank() ? email : String.valueOf(certificate.getAnnulledById());
    }

    public byte[] export(EsgCertificate certificate, List<EsgCertificateLineItem> lineItems) {
        return export(certificate, lineItems, List.of());
    }

    public byte[] export(
            EsgCertificate certificate, List<EsgCertificateLineItem> lineItems, List<DistrictLine> districtLines) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            printer.printRecord("Empresa", text(certificate.getCompanyName()));
            printer.printRecord("RUC", text(certificate.getCompanyRuc()));
            printer.printRecord("Periodo inicio", certificate.getPeriodStart().format(DATE_FORMAT));
            printer.printRecord("Periodo fin", certificate.getPeriodEnd().format(DATE_FORMAT));
            printer.printRecord("Kilos trazados", certificate.getKilosTrazados().toPlainString());
            printer.printRecord(
                    "Cumplimiento de jerarquia (%)", certificate.getHierarchyCompliancePercent().toPlainString());
            printer.printRecord("Emitido", DateTimeFormatter.ISO_INSTANT.format(certificate.getIssuedAt()));
            printer.printRecord("estado", certificate.isAnnulled() ? "Anulado" : "Emitido");
            if (certificate.isAnnulled()) {
                printer.printRecord("anulado_en", DateTimeFormatter.ISO_INSTANT.format(certificate.getAnnulledAt()));
                printer.printRecord("anulado_por", text(annulledBy(certificate)));
                printer.printRecord("motivo_anulacion", text(certificate.getAnnulmentReason()));
            }
            printer.println();

            if (!districtLines.isEmpty()) {
                // SIGERSOL Municipal's aggregated public counts per district, as frozen at issue.
                printer.printRecord(
                        "Distrito",
                        "Ubigeo",
                        "Kilos trazados",
                        "Contexto",
                        "Asociaciones",
                        "Recicladores",
                        "Fuente");
                for (DistrictLine district : districtLines) {
                    boolean available = district.status() == DistrictLine.Status.AVAILABLE;
                    printer.printRecord(
                            text(district.districtName()),
                            district.districtUbigeo() == null ? "" : district.districtUbigeo(),
                            district.kilos().toPlainString(),
                            switch (district.status()) {
                                case AVAILABLE -> "disponible";
                                case UNAVAILABLE -> "no disponible";
                                case UNKNOWN_DISTRICT -> "distrito desconocido";
                            },
                            available ? district.associationCount() : "",
                            available ? district.recyclerCount() : "",
                            available ? "SIGERSOL Municipal, año " + district.year() + ", datos agregados" : "");
                }
                printer.println();
            }

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

package pe.esgtrazabilidad.reporting.certificate.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.certificate.HierarchyDeclaration;
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
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /**
     * RFC 4180: a comma between fields, CRLF between records, and double quotes around any field that
     * holds a comma, a quote or a line break (a quote inside is doubled). Excel splits a double-clicked
     * CSV with the Windows LIST SEPARATOR, which follows the decimal symbol: a comma where the decimal
     * is a dot (en-US, es-MX and, per CLDR, es-PE, the platform's users), a semicolon where it is a
     * comma (es-ES). M7b (Angel): the comma, since it also opens in columns in every parser (pandas,
     * LibreOffice, Numbers) without a {@code sep=} line; on a machine with a decimal comma the file
     * lands in column A (open it from Excel's Data > From Text/CSV and pick "," as the delimiter).
     * It decodes as ANSI unless it opens with a UTF-8 byte-order mark, which is why the BOM is always
     * written. Numbers are written with a dot decimal and never grouped, so they never hold a comma.
     */
    public static final char SEPARATOR = ',';
    public static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder().setDelimiter(SEPARATOR).build();
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    /** Lima wall-clock time, readable in a spreadsheet; the column label says which zone. */
    private static String limaTime(Instant instant) {
        return DATE_TIME_FORMAT.format(instant.atZone(LIMA));
    }

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
        return export(certificate, lineItems, districtLines, HierarchyDeclaration.NOT_RECORDED);
    }

    public byte[] export(
            EsgCertificate certificate,
            List<EsgCertificateLineItem> lineItems,
            List<DistrictLine> districtLines,
            HierarchyDeclaration declaration) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        try (OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
                CSVPrinter printer = new CSVPrinter(writer, FORMAT)) {
            printer.printRecord("Empresa", text(certificate.getCompanyName()));
            printer.printRecord("RUC", text(certificate.getCompanyRuc()));
            printer.printRecord("Período inicio", certificate.getPeriodStart().format(DATE_FORMAT));
            printer.printRecord("Período fin", certificate.getPeriodEnd().format(DATE_FORMAT));
            printer.printRecord("Kilos trazados", certificate.getKilosTrazados().toPlainString());
            // A percentage, written as a plain number; the note row below says whose figure it is.
            printer.printRecord(HierarchyDeclaration.LABEL, certificate.getHierarchyCompliancePercent().toPlainString());
            printer.printRecord("Emitido (hora de Lima)", limaTime(certificate.getIssuedAt()));
            printer.printRecord("Estado", certificate.isAnnulled() ? "Anulado" : "Vigente");
            if (certificate.isAnnulled()) {
                printer.printRecord("Anulado en (hora de Lima)", limaTime(certificate.getAnnulledAt()));
                printer.printRecord("Anulado por", text(annulledBy(certificate)));
                printer.printRecord("Motivo de anulación", text(certificate.getAnnulmentReason()));
            }
            printer.printRecord("Nota sobre la jerarquía", text(declaration.note()));
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
                                case AVAILABLE -> "Disponible";
                                case UNAVAILABLE -> "No disponible";
                                case UNKNOWN_DISTRICT -> "Distrito desconocido";
                            },
                            available ? district.associationCount() : "",
                            available ? district.recyclerCount() : "",
                            available ? "SIGERSOL Municipal, año " + district.year() + ", datos agregados" : "");
                }
                printer.println();
            }

            printer.printRecord("Fecha de recolección", "Peso (kg)");
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

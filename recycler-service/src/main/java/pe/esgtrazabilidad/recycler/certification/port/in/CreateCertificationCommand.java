package pe.esgtrazabilidad.recycler.certification.port.in;

import java.time.LocalDate;
import java.util.UUID;

public record CreateCertificationCommand(
        UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
}

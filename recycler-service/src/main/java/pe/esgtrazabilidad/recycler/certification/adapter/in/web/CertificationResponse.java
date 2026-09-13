package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import java.time.LocalDate;
import java.util.UUID;

public record CertificationResponse(
        UUID id,
        UUID associationId,
        String certificationType,
        LocalDate issuedDate,
        LocalDate expirationDate,
        boolean expired) {
}

package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyVerification;

/** {@code warning} is set when SUNAT reports the taxpayer as NO HABIDO: the company is registered anyway. */
public record TrackedCompanyResponse(
        UUID id,
        String name,
        String ruc,
        UUID associationId,
        TrackedCompanyStatus status,
        TrackedCompanyVerification verification,
        String sunatEstado,
        String sunatCondicion,
        Instant sunatCheckedAt,
        String contactName,
        String contactEmail,
        String contactPhone,
        String warning) {
}

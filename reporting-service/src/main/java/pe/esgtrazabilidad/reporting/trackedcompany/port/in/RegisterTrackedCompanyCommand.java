package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;

/** The legal name is not part of the command: it comes from SUNAT. */
public record RegisterTrackedCompanyCommand(String ruc, UUID associationId, CompanyContact contact) {
}

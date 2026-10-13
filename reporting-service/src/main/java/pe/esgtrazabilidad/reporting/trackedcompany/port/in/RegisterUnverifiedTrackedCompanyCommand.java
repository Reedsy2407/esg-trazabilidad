package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;

/** The fallback for when SUNAT does not answer: here the name is the operator's. */
public record RegisterUnverifiedTrackedCompanyCommand(
        String ruc, String name, UUID associationId, CompanyContact contact) {
}

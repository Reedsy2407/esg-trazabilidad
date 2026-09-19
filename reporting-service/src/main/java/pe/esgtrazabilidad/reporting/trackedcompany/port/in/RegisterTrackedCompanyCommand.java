package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

public record RegisterTrackedCompanyCommand(String name, String ruc, UUID associationId) {
}

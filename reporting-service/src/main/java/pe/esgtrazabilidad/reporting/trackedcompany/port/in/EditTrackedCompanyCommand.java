package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

/**
 * A null field is left as it is; a blank contact field clears it. {@code ruc}
 * and {@code associationId} only count when they differ from the stored ones.
 */
public record EditTrackedCompanyCommand(
        UUID id, String ruc, UUID associationId, String contactName, String contactEmail, String contactPhone) {
}

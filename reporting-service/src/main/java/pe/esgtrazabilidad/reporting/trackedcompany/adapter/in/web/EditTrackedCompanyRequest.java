package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A field that is absent is left as it is; a blank contact field clears it.
 * {@code ruc} and {@code associationId} are refused (RPT-017) once the company
 * has any certificate; a new RUC is looked up in SUNAT again.
 */
public record EditTrackedCompanyRequest(
        @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos numéricos") String ruc,
        UUID associationId,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone) {
}

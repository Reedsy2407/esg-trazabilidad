package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The fallback when SUNAT does not answer: the operator types the name. */
public record RegisterUnverifiedTrackedCompanyRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos numéricos") String ruc,
        @NotBlank @Size(max = 255) String name,
        @NotNull UUID associationId,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone) {
}

package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * {@code name} is deprecated (version N of the two-version retirement): the
 * legal name comes from SUNAT, so a value sent here is ignored. Version N+1
 * answers 400 (RPT-020) when it is present.
 */
public record RegisterTrackedCompanyRequest(
        @NotBlank @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos numéricos") String ruc,
        @NotNull UUID associationId,
        @Size(max = 255) String contactName,
        @Email @Size(max = 255) String contactEmail,
        @Size(max = 30) String contactPhone,
        @Schema(deprecated = true, description = "Ignorado: la razón social viene de SUNAT") String name) {
}

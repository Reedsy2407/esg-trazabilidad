package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record RegisterTrackedCompanyRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos numéricos") String ruc,
        @NotNull UUID associationId) {
}

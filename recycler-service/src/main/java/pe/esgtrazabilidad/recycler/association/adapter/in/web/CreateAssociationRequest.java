package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAssociationRequest(
        @NotBlank String name,
        @NotBlank @Pattern(regexp = "\\d{11}", message = "El RUC debe tener 11 dígitos numéricos") String ruc,
        String registrationNumber,
        String address,
        @Email String contactEmail,
        String contactPhone) {
}

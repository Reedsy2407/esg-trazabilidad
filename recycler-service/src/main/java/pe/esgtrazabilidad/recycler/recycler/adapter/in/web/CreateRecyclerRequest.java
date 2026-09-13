package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRecyclerRequest(
        @NotBlank String fullName,
        @NotBlank @Pattern(regexp = "\\d{8}", message = "El DNI debe tener 8 dígitos numéricos") String dni,
        String phone) {
}

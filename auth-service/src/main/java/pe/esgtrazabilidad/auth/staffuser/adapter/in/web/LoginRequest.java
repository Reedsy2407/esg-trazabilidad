package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String email, @NotBlank String password) {
}

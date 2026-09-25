package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffUserRequest(
        @NotBlank @Email String email, @NotBlank String password, @NotBlank String fullName) {
}

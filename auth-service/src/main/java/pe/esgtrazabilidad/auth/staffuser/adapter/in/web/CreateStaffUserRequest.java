package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

/** {@code role} is optional and defaults to STAFF. The password rules live in the service (AUTH-007). */
public record CreateStaffUserRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 256) String password,
        @NotBlank @Size(max = 255) String fullName,
        StaffRole role) {
}

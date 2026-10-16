package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The admin's browser generates it (like the initial one); the service applies the password policy. */
public record ResetPasswordRequest(@NotBlank @Size(max = 256) String newPassword) {
}

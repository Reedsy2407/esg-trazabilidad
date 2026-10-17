package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The token comes from {@code /verify} (a UUID, a dot and 43 characters: 80 in all). */
public record ConfirmPasswordResetRequest(
        @NotBlank @Size(max = 128) String resetToken,
        @NotBlank @Size(max = 256) String newPassword) {
}

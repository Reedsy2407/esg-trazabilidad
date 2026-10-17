package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The code may arrive with spaces (pasted as "1234 5678"); the service strips them. */
public record VerifyPasswordResetRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 32) String code) {
}

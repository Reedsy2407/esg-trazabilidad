package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No @Email on purpose: whatever the format, the answer is the same 202. */
public record RequestPasswordResetRequest(@NotBlank @Size(max = 254) String email) {
}

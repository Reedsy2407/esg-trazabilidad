package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why an account is being deactivated or reactivated; kept with who did it and when. */
public record StatusReasonRequest(@NotBlank @Size(max = 500) String reason) {
}

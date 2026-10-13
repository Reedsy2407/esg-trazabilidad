package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why a company is deactivated, reactivated or deleted. Who comes from the token, never from here. */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {
}

package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * Replaces every editable field: name, address and district are required,
 * a missing phone clears it. The district is always a catalog ubigeo here
 * (no v2 free-text fallback: no v2 screen edits neighbours).
 */
public record EditNeighborRequest(
        @NotBlank String fullName, String phone, @NotBlank String address, @NotBlank String districtUbigeo) {
}

package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

/**
 * districtUbigeo (6 digits, one of the 50 of Lima Metropolitana and Callao) is
 * the district. district (free text) is the v2 field, accepted only when it
 * names a catalog district; with neither, COL-010.
 */
public record CreateNeighborRequest(
        @NotBlank String fullName, String phone, @NotBlank String address, String districtUbigeo, String district) {
}

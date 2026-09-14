package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import jakarta.validation.constraints.NotBlank;

public record CreateNeighborRequest(@NotBlank String fullName, String phone, @NotBlank String address, String district) {
}

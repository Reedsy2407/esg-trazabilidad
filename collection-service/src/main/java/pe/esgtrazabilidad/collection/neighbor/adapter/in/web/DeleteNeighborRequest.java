package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the neighbour is deleted. Who comes from the token, never from here. */
public record DeleteNeighborRequest(@NotBlank @Size(max = 500) String reason) {
}

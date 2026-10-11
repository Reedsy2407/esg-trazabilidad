package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the neighbour is deactivated or reactivated. Who comes from the token, never from here. */
public record ChangeNeighborStatusRequest(@NotBlank @Size(max = 500) String reason) {
}

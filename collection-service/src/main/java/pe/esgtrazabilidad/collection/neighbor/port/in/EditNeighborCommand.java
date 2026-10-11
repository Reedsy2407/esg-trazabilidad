package pe.esgtrazabilidad.collection.neighbor.port.in;

import java.util.UUID;

/** Replaces all the editable fields at once; a null phone clears it. */
public record EditNeighborCommand(UUID id, String fullName, String phone, String address, String districtUbigeo) {
}

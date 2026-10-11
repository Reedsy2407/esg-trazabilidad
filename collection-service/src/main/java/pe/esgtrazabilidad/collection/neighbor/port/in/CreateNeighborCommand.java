package pe.esgtrazabilidad.collection.neighbor.port.in;

/**
 * districtUbigeo is the v3 way to name the district; district (free text) is
 * still accepted as a fallback when it names a catalog district, so the v2
 * frontend keeps working during the deploy.
 */
public record CreateNeighborCommand(
        String fullName, String phone, String address, String districtUbigeo, String district) {
}

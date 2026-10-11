package pe.esgtrazabilidad.collection.neighbor.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.ubigeo.District;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

public class Neighbor {

    private final UUID id;
    private final String fullName;
    private final String phone;
    private final String address;
    private final String district;
    private final String districtUbigeo;
    private final NeighborStatus status;

    private Neighbor(
            UUID id,
            String fullName,
            String phone,
            String address,
            String district,
            String districtUbigeo,
            NeighborStatus status) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.district = district;
        this.districtUbigeo = districtUbigeo;
        this.status = status;
    }

    /**
     * A new neighbour always has a district from the closed list (v3): the
     * service resolves it from UbigeoCatalog first, so the stored name is the
     * catalog's display name, accents included.
     */
    public static Neighbor create(String fullName, String phone, String address, District district) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección es obligatoria: sin ella no hay lugar físico donde programar el recojo");
        }
        if (district == null) {
            throw new IllegalArgumentException("El distrito es obligatorio");
        }
        return new Neighbor(
                IdGenerator.generate(),
                fullName,
                phone,
                address,
                district.displayName(),
                district.ubigeo(),
                NeighborStatus.ACTIVE);
    }

    /** Rows created before v3 may carry free text in district and a null ubigeo. */
    public static Neighbor reconstruct(
            UUID id,
            String fullName,
            String phone,
            String address,
            String district,
            String districtUbigeo,
            NeighborStatus status) {
        return new Neighbor(id, fullName, phone, address, district, districtUbigeo, status);
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddress() {
        return address;
    }

    /** The stored text: the catalog's name for v3 rows, whatever was typed for older ones. */
    public String getDistrict() {
        return district;
    }

    /** Null only for a pre-v3 row whose free text didn't match the catalog. */
    public String getDistrictUbigeo() {
        return districtUbigeo;
    }

    /** What screens show: the catalog's name when the ubigeo is known, else the stored text. */
    public String getDistrictDisplayName() {
        return UbigeoCatalog.find(districtUbigeo).map(District::displayName).orElse(district);
    }

    public NeighborStatus getStatus() {
        return status;
    }
}

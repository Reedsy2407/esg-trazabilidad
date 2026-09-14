package pe.esgtrazabilidad.collection.neighbor.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Neighbor {

    private final UUID id;
    private final String fullName;
    private final String phone;
    private final String address;
    private final String district;
    private final NeighborStatus status;

    private Neighbor(
            UUID id, String fullName, String phone, String address, String district, NeighborStatus status) {
        this.id = id;
        this.fullName = fullName;
        this.phone = phone;
        this.address = address;
        this.district = district;
        this.status = status;
    }

    public static Neighbor create(String fullName, String phone, String address, String district) {
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException(
                    "La dirección es obligatoria: sin ella no hay lugar físico donde programar el recojo");
        }
        return new Neighbor(IdGenerator.generate(), fullName, phone, address, district, NeighborStatus.ACTIVE);
    }

    public static Neighbor reconstruct(
            UUID id, String fullName, String phone, String address, String district, NeighborStatus status) {
        return new Neighbor(id, fullName, phone, address, district, status);
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

    public String getDistrict() {
        return district;
    }

    public NeighborStatus getStatus() {
        return status;
    }
}

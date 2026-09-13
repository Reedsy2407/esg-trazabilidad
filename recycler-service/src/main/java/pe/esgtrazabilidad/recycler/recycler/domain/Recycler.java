package pe.esgtrazabilidad.recycler.recycler.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Recycler {

    private final UUID id;
    private final String fullName;
    private final String dni;
    private final String phone;
    private final UUID associationId;
    private RecyclerStatus status;

    private Recycler(
            UUID id, String fullName, String dni, String phone, UUID associationId, RecyclerStatus status) {
        this.id = id;
        this.fullName = fullName;
        this.dni = dni;
        this.phone = phone;
        this.associationId = associationId;
        this.status = status;
    }

    public static Recycler create(String fullName, String dni, String phone, UUID associationId) {
        if (dni == null || !dni.matches("\\d{8}")) {
            throw new IllegalArgumentException("El DNI debe tener 8 dígitos numéricos");
        }
        return new Recycler(IdGenerator.generate(), fullName, dni, phone, associationId, RecyclerStatus.ACTIVE);
    }

    public static Recycler reconstruct(
            UUID id, String fullName, String dni, String phone, UUID associationId, RecyclerStatus status) {
        return new Recycler(id, fullName, dni, phone, associationId, status);
    }

    public void deactivate() {
        if (status == RecyclerStatus.INACTIVE) {
            throw new IllegalStateException("El reciclador ya está inactivo");
        }
        status = RecyclerStatus.INACTIVE;
    }

    public void activate() {
        if (status == RecyclerStatus.ACTIVE) {
            throw new IllegalStateException("El reciclador ya está activo");
        }
        status = RecyclerStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getDni() {
        return dni;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public RecyclerStatus getStatus() {
        return status;
    }
}

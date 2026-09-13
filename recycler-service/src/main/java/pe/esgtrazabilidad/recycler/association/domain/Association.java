package pe.esgtrazabilidad.recycler.association.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Association {

    private final UUID id;
    private final String name;
    private final String ruc;
    private final String registrationNumber;
    private final String address;
    private final String contactEmail;
    private final String contactPhone;
    private AssociationStatus status;

    private Association(
            UUID id,
            String name,
            String ruc,
            String registrationNumber,
            String address,
            String contactEmail,
            String contactPhone,
            AssociationStatus status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.registrationNumber = registrationNumber;
        this.address = address;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.status = status;
    }

    public static Association create(
            String name,
            String ruc,
            String registrationNumber,
            String address,
            String contactEmail,
            String contactPhone) {
        if (ruc == null || ruc.length() != 11) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos");
        }
        return new Association(
                IdGenerator.generate(),
                name,
                ruc,
                registrationNumber,
                address,
                contactEmail,
                contactPhone,
                AssociationStatus.ACTIVE);
    }

    public static Association reconstruct(
            UUID id,
            String name,
            String ruc,
            String registrationNumber,
            String address,
            String contactEmail,
            String contactPhone,
            AssociationStatus status) {
        return new Association(id, name, ruc, registrationNumber, address, contactEmail, contactPhone, status);
    }

    public void suspend() {
        if (status == AssociationStatus.SUSPENDED) {
            throw new IllegalStateException("La asociación ya está suspendida");
        }
        status = AssociationStatus.SUSPENDED;
    }

    public void activate() {
        if (status == AssociationStatus.ACTIVE) {
            throw new IllegalStateException("La asociación ya está activa");
        }
        status = AssociationStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRuc() {
        return ruc;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getAddress() {
        return address;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public AssociationStatus getStatus() {
        return status;
    }
}

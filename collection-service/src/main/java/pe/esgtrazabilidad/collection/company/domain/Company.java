package pe.esgtrazabilidad.collection.company.domain;

import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Company {

    private final UUID id;
    private final String name;
    private final String ruc;
    private final String contactEmail;
    private final String contactPhone;
    private final String address;
    private final CompanyStatus status;

    private Company(
            UUID id,
            String name,
            String ruc,
            String contactEmail,
            String contactPhone,
            String address,
            CompanyStatus status) {
        this.id = id;
        this.name = name;
        this.ruc = ruc;
        this.contactEmail = contactEmail;
        this.contactPhone = contactPhone;
        this.address = address;
        this.status = status;
    }

    public static Company create(
            String name, String ruc, String contactEmail, String contactPhone, String address) {
        if (ruc == null || !ruc.matches("\\d{11}")) {
            throw new IllegalArgumentException("El RUC debe tener 11 dígitos numéricos");
        }
        return new Company(
                IdGenerator.generate(), name, ruc, contactEmail, contactPhone, address, CompanyStatus.ACTIVE);
    }

    public static Company reconstruct(
            UUID id,
            String name,
            String ruc,
            String contactEmail,
            String contactPhone,
            String address,
            CompanyStatus status) {
        return new Company(id, name, ruc, contactEmail, contactPhone, address, status);
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

    public String getContactEmail() {
        return contactEmail;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getAddress() {
        return address;
    }

    public CompanyStatus getStatus() {
        return status;
    }
}

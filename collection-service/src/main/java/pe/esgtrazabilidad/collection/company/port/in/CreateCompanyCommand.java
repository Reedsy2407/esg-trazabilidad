package pe.esgtrazabilidad.collection.company.port.in;

public record CreateCompanyCommand(
        String name, String ruc, String contactEmail, String contactPhone, String address) {
}

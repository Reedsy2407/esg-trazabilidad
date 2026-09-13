package pe.esgtrazabilidad.recycler.association.port.in;

public record CreateAssociationCommand(
        String name,
        String ruc,
        String registrationNumber,
        String address,
        String contactEmail,
        String contactPhone) {
}

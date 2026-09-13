package pe.esgtrazabilidad.recycler.recycler.port.in;

import java.util.UUID;

public record CreateRecyclerCommand(String fullName, String dni, String phone, UUID associationId) {
}

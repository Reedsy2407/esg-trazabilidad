package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import java.util.UUID;

import pe.esgtrazabilidad.recycler.recycler.domain.RecyclerStatus;

public record RecyclerResponse(
        UUID id, String fullName, String dni, String phone, UUID associationId, RecyclerStatus status) {
}

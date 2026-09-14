package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import java.util.UUID;

import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;

public record NeighborResponse(
        UUID id, String fullName, String phone, String address, String district, NeighborStatus status) {
}

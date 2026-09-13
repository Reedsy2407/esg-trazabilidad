package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import java.util.UUID;

import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;

public record AssociationResponse(
        UUID id,
        String name,
        String ruc,
        String registrationNumber,
        String address,
        String contactEmail,
        String contactPhone,
        AssociationStatus status) {
}

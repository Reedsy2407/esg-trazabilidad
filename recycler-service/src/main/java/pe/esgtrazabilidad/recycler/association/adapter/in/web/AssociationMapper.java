package pe.esgtrazabilidad.recycler.association.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.in.CreateAssociationCommand;

@Component
class AssociationMapper {

    CreateAssociationCommand toCommand(CreateAssociationRequest request) {
        return new CreateAssociationCommand(
                request.name(),
                request.ruc(),
                request.registrationNumber(),
                request.address(),
                request.contactEmail(),
                request.contactPhone());
    }

    AssociationResponse toResponse(Association association) {
        return new AssociationResponse(
                association.getId(),
                association.getName(),
                association.getRuc(),
                association.getRegistrationNumber(),
                association.getAddress(),
                association.getContactEmail(),
                association.getContactPhone(),
                association.getStatus());
    }
}

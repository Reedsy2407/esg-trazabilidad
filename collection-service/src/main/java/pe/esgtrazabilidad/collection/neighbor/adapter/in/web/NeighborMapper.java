package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatusChange;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;
import pe.esgtrazabilidad.collection.neighbor.port.in.EditNeighborCommand;

@Component
class NeighborMapper {

    CreateNeighborCommand toCommand(CreateNeighborRequest request) {
        return new CreateNeighborCommand(
                request.fullName(), request.phone(), request.address(), request.districtUbigeo(), request.district());
    }

    EditNeighborCommand toCommand(UUID id, EditNeighborRequest request) {
        return new EditNeighborCommand(id, request.fullName(), request.phone(), request.address(), request.districtUbigeo());
    }

    NeighborStatusChangeResponse toResponse(NeighborStatusChange change) {
        return new NeighborStatusChangeResponse(
                change.id(),
                change.fromStatus(),
                change.toStatus(),
                change.reason(),
                change.actorId(),
                change.actorEmail(),
                change.changedAt());
    }

    NeighborResponse toResponse(Neighbor neighbor) {
        return new NeighborResponse(
                neighbor.getId(),
                neighbor.getFullName(),
                neighbor.getPhone(),
                neighbor.getAddress(),
                neighbor.getDistrictDisplayName(),
                neighbor.getDistrictUbigeo(),
                neighbor.getStatus());
    }
}

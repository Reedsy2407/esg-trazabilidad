package pe.esgtrazabilidad.collection.neighbor.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.port.in.CreateNeighborCommand;

@Component
class NeighborMapper {

    CreateNeighborCommand toCommand(CreateNeighborRequest request) {
        return new CreateNeighborCommand(request.fullName(), request.phone(), request.address(), request.district());
    }

    NeighborResponse toResponse(Neighbor neighbor) {
        return new NeighborResponse(
                neighbor.getId(),
                neighbor.getFullName(),
                neighbor.getPhone(),
                neighbor.getAddress(),
                neighbor.getDistrict(),
                neighbor.getStatus());
    }
}

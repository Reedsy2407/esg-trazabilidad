package pe.esgtrazabilidad.recycler.recycler.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.port.in.CreateRecyclerCommand;

@Component
class RecyclerMapper {

    CreateRecyclerCommand toCommand(UUID associationId, CreateRecyclerRequest request) {
        return new CreateRecyclerCommand(request.fullName(), request.dni(), request.phone(), associationId);
    }

    RecyclerResponse toResponse(Recycler recycler) {
        return new RecyclerResponse(
                recycler.getId(),
                recycler.getFullName(),
                recycler.getDni(),
                recycler.getPhone(),
                recycler.getAssociationId(),
                recycler.getStatus());
    }
}

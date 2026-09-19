package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;

@Component
class TrackedCompanyMapper {

    RegisterTrackedCompanyCommand toCommand(RegisterTrackedCompanyRequest request) {
        return new RegisterTrackedCompanyCommand(request.name(), request.ruc(), request.associationId());
    }

    TrackedCompanyResponse toResponse(TrackedCompany trackedCompany) {
        return new TrackedCompanyResponse(
                trackedCompany.getId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                trackedCompany.getAssociationId(),
                trackedCompany.getStatus());
    }
}

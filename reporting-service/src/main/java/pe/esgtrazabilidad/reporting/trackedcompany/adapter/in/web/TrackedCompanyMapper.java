package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.EditTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterTrackedCompanyCommand;
import pe.esgtrazabilidad.reporting.trackedcompany.port.in.RegisterUnverifiedTrackedCompanyCommand;

@Component
class TrackedCompanyMapper {

    static final String NOT_LOCATED_WARNING = "SUNAT indica que el contribuyente figura como NO HABIDO";

    RegisterTrackedCompanyCommand toCommand(RegisterTrackedCompanyRequest request) {
        return new RegisterTrackedCompanyCommand(
                request.ruc(),
                request.associationId(),
                new CompanyContact(
                        blankToNull(request.contactName()),
                        blankToNull(request.contactEmail()),
                        blankToNull(request.contactPhone())));
    }

    RegisterUnverifiedTrackedCompanyCommand toCommand(RegisterUnverifiedTrackedCompanyRequest request) {
        return new RegisterUnverifiedTrackedCompanyCommand(
                request.ruc(),
                request.name(),
                request.associationId(),
                new CompanyContact(
                        blankToNull(request.contactName()),
                        blankToNull(request.contactEmail()),
                        blankToNull(request.contactPhone())));
    }

    EditTrackedCompanyCommand toCommand(UUID id, EditTrackedCompanyRequest request) {
        return new EditTrackedCompanyCommand(
                id,
                request.ruc(),
                request.associationId(),
                request.contactName(),
                request.contactEmail(),
                request.contactPhone());
    }

    TrackedCompanyResponse toResponse(TrackedCompany trackedCompany) {
        return new TrackedCompanyResponse(
                trackedCompany.getId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                trackedCompany.getAssociationId(),
                trackedCompany.getStatus(),
                trackedCompany.getVerification(),
                trackedCompany.getSunatEstado(),
                trackedCompany.getSunatCondicion(),
                trackedCompany.getSunatCheckedAt(),
                trackedCompany.getContact().name(),
                trackedCompany.getContact().email(),
                trackedCompany.getContact().phone(),
                trackedCompany.isNotLocated() ? NOT_LOCATED_WARNING : null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

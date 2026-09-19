package pe.esgtrazabilidad.reporting.trackedcompany.adapter.in.web;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;

public record TrackedCompanyResponse(
        UUID id, String name, String ruc, UUID associationId, TrackedCompanyStatus status) {
}

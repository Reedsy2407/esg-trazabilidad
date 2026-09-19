package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

public interface GetTrackedCompanyUseCase {

    TrackedCompany getById(UUID id);
}

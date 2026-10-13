package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

public interface RegisterUnverifiedTrackedCompanyUseCase {

    TrackedCompany registerUnverified(RegisterUnverifiedTrackedCompanyCommand command);
}

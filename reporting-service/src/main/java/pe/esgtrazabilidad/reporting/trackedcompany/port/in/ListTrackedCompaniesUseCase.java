package pe.esgtrazabilidad.reporting.trackedcompany.port.in;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

public interface ListTrackedCompaniesUseCase {

    Page<TrackedCompany> list(Pageable pageable);
}

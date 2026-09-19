package pe.esgtrazabilidad.reporting.trackedcompany.port.out;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

public interface TrackedCompanyRepository {

    TrackedCompany save(TrackedCompany trackedCompany);

    Optional<TrackedCompany> findById(UUID id);

    Optional<TrackedCompany> findByRuc(String ruc);

    Page<TrackedCompany> findAll(Pageable pageable);
}

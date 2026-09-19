package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface TrackedCompanyJpaRepository extends JpaRepository<TrackedCompanyEntity, UUID> {
}

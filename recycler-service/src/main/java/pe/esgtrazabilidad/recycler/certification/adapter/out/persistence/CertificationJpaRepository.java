package pe.esgtrazabilidad.recycler.certification.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface CertificationJpaRepository extends JpaRepository<CertificationEntity, UUID> {
}

package pe.esgtrazabilidad.reporting.sigersolsync.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface SigersolSyncJpaRepository extends JpaRepository<SigersolSyncEntity, UUID> {
}

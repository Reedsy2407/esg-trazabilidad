package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StaffUserJpaRepository extends JpaRepository<StaffUserEntity, UUID> {

    Optional<StaffUserEntity> findByEmail(String email);
}

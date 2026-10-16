package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

interface StaffUserJpaRepository extends JpaRepository<StaffUserEntity, UUID> {

    Optional<StaffUserEntity> findByEmail(String email);

    boolean existsByRoleAndActiveTrue(StaffRole role);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<StaffUserEntity> findByRoleAndActiveTrue(StaffRole role);

    @Modifying(clearAutomatically = true)
    @Query("update StaffUserEntity e set e.passwordHash = :hash, e.mustChangePassword = :must, e.passwordChangedAt = :at"
            + " where e.id = :id")
    int updatePassword(
            @Param("id") UUID id, @Param("hash") String hash, @Param("must") boolean must, @Param("at") Instant at);
}

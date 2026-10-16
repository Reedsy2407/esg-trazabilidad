package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

@Component
class StaffUserRepositoryAdapter implements StaffUserRepository {

    private final StaffUserJpaRepository jpaRepository;

    StaffUserRepositoryAdapter(StaffUserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /** Insert for an id the table doesn't have yet, update otherwise. */
    @Override
    public StaffUser save(StaffUser staffUser) {
        boolean isNew = !jpaRepository.existsById(staffUser.getId());
        return toDomain(jpaRepository.save(toEntity(staffUser, isNew)));
    }

    @Override
    public Optional<StaffUser> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<StaffUser> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public List<StaffUser> findAll() {
        return jpaRepository.findAll(Sort.by("createdAt", "email")).stream().map(this::toDomain).toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional
    public void updatePassword(UUID id, String passwordHash, boolean mustChangePassword, java.time.Instant changedAt) {
        jpaRepository.updatePassword(id, passwordHash, mustChangePassword, changedAt);
    }

    @Override
    public void lockActiveAdmins() {
        jpaRepository.findByRoleAndActiveTrue(StaffRole.ADMIN);
    }

    @Override
    public boolean existsAny() {
        return jpaRepository.count() > 0;
    }

    @Override
    public boolean existsActiveAdmin() {
        return jpaRepository.existsByRoleAndActiveTrue(StaffRole.ADMIN);
    }

    private StaffUserEntity toEntity(StaffUser staffUser, boolean isNew) {
        return new StaffUserEntity(
                staffUser.getId(),
                staffUser.getEmail(),
                staffUser.getPasswordHash(),
                staffUser.getFullName(),
                staffUser.isActive(),
                staffUser.getCreatedAt(),
                staffUser.getRole(),
                staffUser.getPasswordChangedAt(),
                staffUser.isMustChangePassword(),
                staffUser.getStatusReason(),
                staffUser.getStatusChangedBy(),
                staffUser.getStatusChangedAt(),
                isNew);
    }

    private StaffUser toDomain(StaffUserEntity entity) {
        return StaffUser.reconstruct(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.isActive(),
                entity.getCreatedAt(),
                entity.getRole(),
                entity.getPasswordChangedAt(),
                entity.isMustChangePassword(),
                entity.getStatusReason(),
                entity.getStatusChangedBy(),
                entity.getStatusChangedAt());
    }
}

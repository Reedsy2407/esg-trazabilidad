package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

@Component
class StaffUserRepositoryAdapter implements StaffUserRepository {

    private final StaffUserJpaRepository jpaRepository;

    StaffUserRepositoryAdapter(StaffUserJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public StaffUser save(StaffUser staffUser) {
        return toDomain(jpaRepository.save(toEntity(staffUser)));
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
    public boolean existsAny() {
        return jpaRepository.count() > 0;
    }

    private StaffUserEntity toEntity(StaffUser staffUser) {
        return new StaffUserEntity(
                staffUser.getId(),
                staffUser.getEmail(),
                staffUser.getPasswordHash(),
                staffUser.getFullName(),
                staffUser.isActive(),
                staffUser.getCreatedAt());
    }

    private StaffUser toDomain(StaffUserEntity entity) {
        return StaffUser.reconstruct(
                entity.getId(),
                entity.getEmail(),
                entity.getPasswordHash(),
                entity.getFullName(),
                entity.isActive(),
                entity.getCreatedAt());
    }
}

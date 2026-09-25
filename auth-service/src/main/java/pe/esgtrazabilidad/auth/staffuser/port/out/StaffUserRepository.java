package pe.esgtrazabilidad.auth.staffuser.port.out;

import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;

public interface StaffUserRepository {

    StaffUser save(StaffUser staffUser);

    Optional<StaffUser> findById(UUID id);

    Optional<StaffUser> findByEmail(String email);

    boolean existsAny();
}

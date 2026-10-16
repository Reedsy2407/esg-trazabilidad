package pe.esgtrazabilidad.auth.staffuser.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;

public interface StaffUserRepository {

    /** Inserts an account the table doesn't have yet and updates one it does. */
    StaffUser save(StaffUser staffUser);

    Optional<StaffUser> findById(UUID id);

    Optional<StaffUser> findByEmail(String email);

    /** Every account, oldest first (there are a handful; no paging). */
    List<StaffUser> findAll();

    /**
     * Writes only the password columns. A password change loads the account, runs BCrypt (about
     * 0.6 s) and then writes: saving the whole row would silently undo a deactivation or role
     * change made by another admin in between.
     */
    void updatePassword(UUID id, String passwordHash, boolean mustChangePassword, java.time.Instant changedAt);

    /**
     * Locks the active admins' rows until the transaction ends, so two admins deactivating each
     * other at the same time are serialised and the second sees the first's result.
     */
    void lockActiveAdmins();

    boolean existsAny();

    boolean existsActiveAdmin();
}

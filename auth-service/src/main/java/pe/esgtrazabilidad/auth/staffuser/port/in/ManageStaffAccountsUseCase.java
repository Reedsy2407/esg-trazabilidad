package pe.esgtrazabilidad.auth.staffuser.port.in;

import java.util.List;
import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;

/**
 * What an ADMIN does with other people's accounts. Every method checks, from the database, that
 * {@code actorId} is an active admin (AUTH-006 otherwise).
 */
public interface ManageStaffAccountsUseCase {

    List<StaffUser> list(UUID actorId);

    /** AUTH-009 for the admin's own account, AUTH-010 if it is already inactive. */
    StaffUser deactivate(UUID actorId, UUID targetId, String reason);

    /** AUTH-010 if it is already active. */
    StaffUser reactivate(UUID actorId, UUID targetId, String reason);

    /**
     * Sets a new password for someone else (AUTH-009 for the admin's own: they change theirs with
     * their current one). The person is asked to pick their own at the next sign-in.
     */
    void resetPassword(UUID actorId, UUID targetId, String newRawPassword);
}

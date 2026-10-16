package pe.esgtrazabilidad.auth.staffuser.domain;

/**
 * ADMIN creates, deactivates and resets accounts; STAFF does everything else the platform offers.
 * The role travels in the JWT only for the screens to show or hide things: auth-service decides
 * every ADMIN-only action from the database, never from the token (a deactivated or demoted
 * admin must not keep the power for the rest of its token's hour). The other services don't use it.
 */
public enum StaffRole {
    ADMIN,
    STAFF
}

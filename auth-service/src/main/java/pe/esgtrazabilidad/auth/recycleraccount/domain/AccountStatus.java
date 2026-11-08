package pe.esgtrazabilidad.auth.recycleraccount.domain;

/**
 * Stored states of a recycler account. {@code INACTIVE} is never written: a recycler turned off in
 * recycler-service shows as INACTIVE because the live copy says so (it comes back by itself when the
 * recycler is reactivated), so there is no second state to keep in sync.
 */
public enum AccountStatus {
    PENDING_ACTIVATION,
    ACTIVE,
    INACTIVE
}

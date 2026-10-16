package pe.esgtrazabilidad.auth.staffuser.domain;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * passwordHash is always pre-hashed by the caller (BCrypt); this class never sees a raw password.
 * Immutable: every change returns a new instance. {@code mustChangePassword} is set when someone
 * else chose the password (creation, an admin's reset): the screens send the person to "cambiar mi
 * contraseña" until they pick their own. The last status change keeps its reason, who and when.
 */
public class StaffUser {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final boolean active;
    private final Instant createdAt;
    private final StaffRole role;
    private final Instant passwordChangedAt;
    private final boolean mustChangePassword;
    private final String statusReason;
    private final UUID statusChangedBy;
    private final Instant statusChangedAt;

    private StaffUser(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            boolean active,
            Instant createdAt,
            StaffRole role,
            Instant passwordChangedAt,
            boolean mustChangePassword,
            String statusReason,
            UUID statusChangedBy,
            Instant statusChangedAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.active = active;
        this.createdAt = createdAt;
        this.role = role;
        this.passwordChangedAt = passwordChangedAt;
        this.mustChangePassword = mustChangePassword;
        this.statusReason = statusReason;
        this.statusChangedBy = statusChangedBy;
        this.statusChangedAt = statusChangedAt;
    }

    /** Stricter than Bean Validation's @Email, which accepts a domain with no dot ("ana@empresa"). */
    public static boolean isValidEmail(String email) {
        return email != null && email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    /** A STAFF account whose password is the one given (the person is not asked to change it). */
    public static StaffUser create(String email, String passwordHash, String fullName) {
        return create(email, passwordHash, fullName, StaffRole.STAFF, false);
    }

    public static StaffUser create(
            String email, String passwordHash, String fullName, StaffRole role, boolean mustChangePassword) {
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("El email no es válido");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        if (role == null) {
            throw new IllegalArgumentException("El rol es obligatorio");
        }
        return new StaffUser(
                IdGenerator.generate(),
                email.toLowerCase(),
                passwordHash,
                fullName,
                true,
                Instant.now(),
                role,
                null,
                mustChangePassword,
                null,
                null,
                null);
    }

    /** Rebuilds an existing account as a STAFF with no password or status history (tests, old rows). */
    public static StaffUser reconstruct(
            UUID id, String email, String passwordHash, String fullName, boolean active, Instant createdAt) {
        return reconstruct(
                id, email, passwordHash, fullName, active, createdAt, StaffRole.STAFF, null, false, null, null, null);
    }

    public static StaffUser reconstruct(
            UUID id,
            String email,
            String passwordHash,
            String fullName,
            boolean active,
            Instant createdAt,
            StaffRole role,
            Instant passwordChangedAt,
            boolean mustChangePassword,
            String statusReason,
            UUID statusChangedBy,
            Instant statusChangedAt) {
        return new StaffUser(
                id,
                email,
                passwordHash,
                fullName,
                active,
                createdAt,
                role,
                passwordChangedAt,
                mustChangePassword,
                statusReason,
                statusChangedBy,
                statusChangedAt);
    }

    /** The person chose it themselves ({@code mustChangePassword = false}) or someone else did (true). */
    public StaffUser withPassword(String newPasswordHash, boolean mustChangePassword, Instant now) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        return copy(newPasswordHash, active, role, now, mustChangePassword, statusReason, statusChangedBy, statusChangedAt);
    }

    public StaffUser deactivate(String reason, UUID byId, Instant now) {
        return copy(passwordHash, false, role, passwordChangedAt, mustChangePassword, reason, byId, now);
    }

    public StaffUser reactivate(String reason, UUID byId, Instant now) {
        return copy(passwordHash, true, role, passwordChangedAt, mustChangePassword, reason, byId, now);
    }

    public StaffUser promoteToAdmin() {
        return copy(
                passwordHash, active, StaffRole.ADMIN, passwordChangedAt, mustChangePassword, statusReason, statusChangedBy, statusChangedAt);
    }

    private StaffUser copy(
            String newHash,
            boolean newActive,
            StaffRole newRole,
            Instant newPasswordChangedAt,
            boolean newMustChange,
            String newReason,
            UUID newChangedBy,
            Instant newChangedAt) {
        return new StaffUser(
                id,
                email,
                newHash,
                fullName,
                newActive,
                createdAt,
                newRole,
                newPasswordChangedAt,
                newMustChange,
                newReason,
                newChangedBy,
                newChangedAt);
    }

    public boolean isAdmin() {
        return role == StaffRole.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StaffRole getRole() {
        return role;
    }

    public Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public UUID getStatusChangedBy() {
        return statusChangedBy;
    }

    public Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}

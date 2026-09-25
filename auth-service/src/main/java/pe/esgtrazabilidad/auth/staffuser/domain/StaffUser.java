package pe.esgtrazabilidad.auth.staffuser.domain;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * No role field, deliberately: every current user-facing flow across this
 * platform treats "staff" as one undifferentiated group with full access
 * to every endpoint it calls -- see SPEC-auth-service.md's Resolved
 * Decisions. passwordHash is always pre-hashed by the caller (BCrypt);
 * this class never sees a raw password.
 */
public class StaffUser {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final String fullName;
    private final boolean active;
    private final Instant createdAt;

    private StaffUser(
            UUID id, String email, String passwordHash, String fullName, boolean active, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.active = active;
        this.createdAt = createdAt;
    }

    public static StaffUser create(String email, String passwordHash, String fullName) {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El email no es válido");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("La contraseña es obligatoria");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("El nombre completo es obligatorio");
        }
        return new StaffUser(IdGenerator.generate(), email.toLowerCase(), passwordHash, fullName, true, Instant.now());
    }

    public static StaffUser reconstruct(
            UUID id, String email, String passwordHash, String fullName, boolean active, Instant createdAt) {
        return new StaffUser(id, email, passwordHash, fullName, active, createdAt);
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
}

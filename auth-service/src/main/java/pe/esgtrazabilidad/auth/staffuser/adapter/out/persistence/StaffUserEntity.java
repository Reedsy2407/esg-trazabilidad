package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

@Entity
@Table(name = "staff_user")
class StaffUserEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffRole role;

    @Column(name = "password_changed_at")
    private Instant passwordChangedAt;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    @Column(name = "status_changed_at")
    private Instant statusChangedAt;

    @Transient
    private boolean isNew = false;

    protected StaffUserEntity() {
    }

    StaffUserEntity(
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
            Instant statusChangedAt,
            boolean isNew) {
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
        this.isNew = isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    String getEmail() {
        return email;
    }

    String getPasswordHash() {
        return passwordHash;
    }

    String getFullName() {
        return fullName;
    }

    boolean isActive() {
        return active;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    StaffRole getRole() {
        return role;
    }

    Instant getPasswordChangedAt() {
        return passwordChangedAt;
    }

    boolean isMustChangePassword() {
        return mustChangePassword;
    }

    String getStatusReason() {
        return statusReason;
    }

    UUID getStatusChangedBy() {
        return statusChangedBy;
    }

    Instant getStatusChangedAt() {
        return statusChangedAt;
    }
}

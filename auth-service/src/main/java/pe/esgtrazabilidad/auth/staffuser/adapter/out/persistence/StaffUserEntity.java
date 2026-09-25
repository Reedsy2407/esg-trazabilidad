package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

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

    @Transient
    private boolean isNew = false;

    protected StaffUserEntity() {
    }

    StaffUserEntity(UUID id, String email, String passwordHash, String fullName, boolean active, Instant createdAt) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.active = active;
        this.createdAt = createdAt;
        this.isNew = true;
    }

    @PostLoad
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
}

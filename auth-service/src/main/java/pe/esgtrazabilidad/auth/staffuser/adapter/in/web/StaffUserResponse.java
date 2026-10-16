package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

/**
 * {@code statusReason}, {@code statusChangedAt} and {@code statusChangedBy} (an email, filled in the
 * admin list) describe the last deactivation or reactivation, and are null for an account that has
 * never had one. {@code passwordChangedAt} is null until the first change.
 */
public record StaffUserResponse(
        UUID id,
        String email,
        String fullName,
        StaffRole role,
        boolean active,
        Instant createdAt,
        Instant passwordChangedAt,
        boolean mustChangePassword,
        String statusReason,
        Instant statusChangedAt,
        String statusChangedBy) {
}

package pe.esgtrazabilidad.auth.staffuser.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

/** {@code actorId} is the signed-in admin (from the token); {@code role} defaults to STAFF when null. */
public record CreateStaffUserCommand(UUID actorId, String email, String rawPassword, String fullName, StaffRole role) {
}

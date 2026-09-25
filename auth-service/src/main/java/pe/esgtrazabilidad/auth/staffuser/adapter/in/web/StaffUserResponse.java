package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

public record StaffUserResponse(UUID id, String email, String fullName, boolean active, Instant createdAt) {
}

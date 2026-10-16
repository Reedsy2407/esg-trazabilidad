package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;

@Component
class StaffUserMapper {

    CreateStaffUserCommand toCommand(UUID actorId, CreateStaffUserRequest request) {
        return new CreateStaffUserCommand(
                actorId, request.email(), request.password(), request.fullName(), request.role());
    }

    /** One account; the email of whoever changed its status is not resolved (only the list does that). */
    StaffUserResponse toResponse(StaffUser staffUser) {
        return toResponse(staffUser, Map.of());
    }

    List<StaffUserResponse> toResponses(List<StaffUser> staffUsers) {
        Map<UUID, String> emails = staffUsers.stream().collect(Collectors.toMap(StaffUser::getId, StaffUser::getEmail));
        return staffUsers.stream().map(user -> toResponse(user, emails)).toList();
    }

    private StaffUserResponse toResponse(StaffUser staffUser, Map<UUID, String> emails) {
        Function<UUID, String> emailOf = id -> id == null ? null : emails.get(id);
        return new StaffUserResponse(
                staffUser.getId(),
                staffUser.getEmail(),
                staffUser.getFullName(),
                staffUser.getRole(),
                staffUser.isActive(),
                staffUser.getCreatedAt(),
                staffUser.getPasswordChangedAt(),
                staffUser.isMustChangePassword(),
                staffUser.getStatusReason(),
                staffUser.getStatusChangedAt(),
                emailOf.apply(staffUser.getStatusChangedBy()));
    }
}

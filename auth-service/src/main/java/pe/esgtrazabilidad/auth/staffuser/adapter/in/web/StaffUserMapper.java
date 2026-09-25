package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;

@Component
class StaffUserMapper {

    CreateStaffUserCommand toCommand(CreateStaffUserRequest request) {
        return new CreateStaffUserCommand(request.email(), request.password(), request.fullName());
    }

    StaffUserResponse toResponse(StaffUser staffUser) {
        return new StaffUserResponse(
                staffUser.getId(),
                staffUser.getEmail(),
                staffUser.getFullName(),
                staffUser.isActive(),
                staffUser.getCreatedAt());
    }
}

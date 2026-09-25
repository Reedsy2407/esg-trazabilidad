package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserUseCase;

/**
 * Requires a valid token (everything not on SecurityConfig's permitAll list
 * does) -- any authenticated staff member can onboard another, no
 * admin-only role, per SPEC-auth-service.md's Resolved Decisions.
 */
@RestController
@RequestMapping("/auth/staff-users")
class StaffUserController {

    private final CreateStaffUserUseCase createStaffUserUseCase;
    private final StaffUserMapper mapper;

    StaffUserController(CreateStaffUserUseCase createStaffUserUseCase, StaffUserMapper mapper) {
        this.createStaffUserUseCase = createStaffUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<StaffUserResponse> create(@Valid @RequestBody CreateStaffUserRequest request) {
        StaffUser staffUser = createStaffUserUseCase.createStaffUser(mapper.toCommand(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(staffUser));
    }
}

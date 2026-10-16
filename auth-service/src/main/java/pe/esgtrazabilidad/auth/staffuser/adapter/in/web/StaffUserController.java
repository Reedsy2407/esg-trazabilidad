package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.ManageStaffAccountsUseCase;
import pe.esgtrazabilidad.kernel.security.CurrentActor;

/**
 * Every route here is for an ADMIN. It is checked in the service from the database (AUTH-006), not
 * from the token's role claim, so the controller only passes the signed-in person's id down.
 */
@RestController
@RequestMapping("/auth/staff-users")
class StaffUserController {

    private final CreateStaffUserUseCase createStaffUserUseCase;
    private final ManageStaffAccountsUseCase manageStaffAccountsUseCase;
    private final StaffUserMapper mapper;

    StaffUserController(
            CreateStaffUserUseCase createStaffUserUseCase,
            ManageStaffAccountsUseCase manageStaffAccountsUseCase,
            StaffUserMapper mapper) {
        this.createStaffUserUseCase = createStaffUserUseCase;
        this.manageStaffAccountsUseCase = manageStaffAccountsUseCase;
        this.mapper = mapper;
    }

    @GetMapping
    List<StaffUserResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return mapper.toResponses(manageStaffAccountsUseCase.list(CurrentActor.from(jwt).id()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<StaffUserResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateStaffUserRequest request) {
        StaffUser staffUser =
                createStaffUserUseCase.createStaffUser(mapper.toCommand(CurrentActor.from(jwt).id(), request));
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(staffUser));
    }

    @PatchMapping("/{id}/deactivate")
    StaffUserResponse deactivate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody StatusReasonRequest request) {
        return mapper.toResponse(manageStaffAccountsUseCase.deactivate(CurrentActor.from(jwt).id(), id, request.reason()));
    }

    @PatchMapping("/{id}/reactivate")
    StaffUserResponse reactivate(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody StatusReasonRequest request) {
        return mapper.toResponse(manageStaffAccountsUseCase.reactivate(CurrentActor.from(jwt).id(), id, request.reason()));
    }

    @PostMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetPassword(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest request) {
        manageStaffAccountsUseCase.resetPassword(CurrentActor.from(jwt).id(), id, request.newPassword());
    }
}

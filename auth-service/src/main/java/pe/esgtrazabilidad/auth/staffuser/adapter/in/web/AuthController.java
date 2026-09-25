package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.GetCurrentStaffUserUseCase;
import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;

/**
 * login() has no Mapper involvement, unlike most other controllers in this
 * codebase: LoginUseCase takes two primitives directly (no Command object
 * to build) and returns a plain String (no domain object to translate).
 * me() reuses StaffUserMapper/StaffUserResponse (same package) instead of
 * introducing a separate DTO -- the current staff user's own identity is
 * exactly the same shape StaffUserController already returns.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final LoginUseCase loginUseCase;
    private final GetCurrentStaffUserUseCase getCurrentStaffUserUseCase;
    private final StaffUserMapper mapper;

    AuthController(
            LoginUseCase loginUseCase, GetCurrentStaffUserUseCase getCurrentStaffUserUseCase, StaffUserMapper mapper) {
        this.loginUseCase = loginUseCase;
        this.getCurrentStaffUserUseCase = getCurrentStaffUserUseCase;
        this.mapper = mapper;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String accessToken = loginUseCase.login(request.email(), request.password());
        return new LoginResponse(accessToken);
    }

    @GetMapping("/me")
    StaffUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID staffUserId = UUID.fromString(jwt.getSubject());
        StaffUser staffUser = getCurrentStaffUserUseCase.getCurrent(staffUserId);
        return mapper.toResponse(staffUser);
    }
}

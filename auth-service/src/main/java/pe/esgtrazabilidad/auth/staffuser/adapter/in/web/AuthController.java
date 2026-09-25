package pe.esgtrazabilidad.auth.staffuser.adapter.in.web;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.staffuser.port.in.LoginUseCase;

/**
 * No Mapper class here, unlike most other controllers in this codebase:
 * LoginUseCase takes two primitives directly (no Command object to build)
 * and returns a plain String (no domain object to translate) -- a mapper
 * would be a pure pass-through with nothing to translate.
 */
@RestController
@RequestMapping("/auth")
class AuthController {

    private final LoginUseCase loginUseCase;

    AuthController(LoginUseCase loginUseCase) {
        this.loginUseCase = loginUseCase;
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest request) {
        String accessToken = loginUseCase.login(request.email(), request.password());
        return new LoginResponse(accessToken);
    }
}

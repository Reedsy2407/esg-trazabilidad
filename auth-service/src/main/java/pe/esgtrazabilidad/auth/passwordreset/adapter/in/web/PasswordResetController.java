package pe.esgtrazabilidad.auth.passwordreset.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.passwordreset.port.in.PasswordResetUseCase;
import pe.esgtrazabilidad.auth.ratelimit.ClientIpResolver;

/**
 * Public (no token): the person has no session. All three routes are limited by client address in the
 * database, not in memory. {@code request} always answers 202 with no body, whether or not an email was sent.
 */
@RestController
@RequestMapping("/auth/password-reset")
class PasswordResetController {

    private final PasswordResetUseCase useCase;
    private final ClientIpResolver clientIpResolver;

    PasswordResetController(PasswordResetUseCase useCase, ClientIpResolver clientIpResolver) {
        this.useCase = useCase;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void request(@Valid @RequestBody RequestPasswordResetRequest body, HttpServletRequest http) {
        useCase.request(body.email(), clientIpResolver.resolve(http));
    }

    @PostMapping("/verify")
    VerifyPasswordResetResponse verify(@Valid @RequestBody VerifyPasswordResetRequest body, HttpServletRequest http) {
        return new VerifyPasswordResetResponse(useCase.verify(body.email(), body.code(), clientIpResolver.resolve(http)));
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirm(@Valid @RequestBody ConfirmPasswordResetRequest body, HttpServletRequest http) {
        useCase.confirm(body.resetToken(), body.newPassword(), clientIpResolver.resolve(http));
    }
}

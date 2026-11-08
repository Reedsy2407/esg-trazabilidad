package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.auth.ratelimit.ClientIpResolver;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerActivationUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerConsentUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerSessionUseCase;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/**
 * The recycler's own routes under /auth/recycler. {@code activate} is public (the person has no session
 * yet) and limited by client address and by code in the database; {@code refresh} is public too, its
 * credential being the opaque refresh token; {@code logout} and the consent routes need a RECYCLER token. Responses carrying
 * tokens are not cacheable.
 */
@RestController
@RequestMapping("/auth/recycler")
class RecyclerSessionController {

    private final RecyclerActivationUseCase activation;
    private final RecyclerSessionUseCase session;
    private final RecyclerConsentUseCase consent;
    private final ClientIpResolver clientIpResolver;

    RecyclerSessionController(
            RecyclerActivationUseCase activation,
            RecyclerSessionUseCase session,
            RecyclerConsentUseCase consent,
            ClientIpResolver clientIpResolver) {
        this.activation = activation;
        this.session = session;
        this.consent = consent;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/activate")
    ResponseEntity<ActivationResponse> activate(@Valid @RequestBody ActivateRequest body, HttpServletRequest http) {
        var result = activation.activate(
                body.code(), body.deviceLabel(), body.deviceInstallId(), clientIpResolver.resolve(http));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ActivationResponse.from(result));
    }

    @PostMapping("/refresh")
    ResponseEntity<RefreshResponse> refresh(@Valid @RequestBody RefreshRequest body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(RefreshResponse.from(session.refresh(body.refreshToken())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RefreshRequest body) {
        session.logout(RecyclerClaims.from(jwt).accountId(), body.refreshToken());
    }

    @GetMapping("/consent")
    ConsentResponse consent() {
        return ConsentResponse.from(consent.current());
    }

    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void answerConsent(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ConsentRequest body) {
        consent.answer(RecyclerClaims.from(jwt).accountId(), body.version(), body.accepted(), body.locationAccepted());
    }
}

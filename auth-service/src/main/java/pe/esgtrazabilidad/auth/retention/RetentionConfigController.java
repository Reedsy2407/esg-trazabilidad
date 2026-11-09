package pe.esgtrazabilidad.auth.retention;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import pe.esgtrazabilidad.kernel.security.CurrentActor;

/**
 * The retention periods the recycler's consent shows. ADMIN only: SecurityConfig answers 403 AUTH-000 to
 * any other role, and the service checks it again from the database (a demoted admin loses it at once).
 */
@RestController
@RequestMapping("/auth/retention-config")
class RetentionConfigController {

    private final RetentionService service;

    RetentionConfigController(RetentionService service) {
        this.service = service;
    }

    @GetMapping
    RetentionConfigResponse get() {
        return RetentionConfigResponse.from(service.current());
    }

    @PutMapping
    RetentionConfigResponse update(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RetentionConfigRequest request) {
        return RetentionConfigResponse.from(
                service.update(CurrentActor.from(jwt).id(), request.gpsDays(), request.photoMonths()));
    }
}

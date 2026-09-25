package pe.esgtrazabilidad.auth.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoint, picked up by auth-service's own component scan since
 * it lives under pe.esgtrazabilidad.auth. Exists purely to prove
 * SecurityConfig's "everything except the explicit permitAll list requires
 * a token" behavior end-to-end over real HTTP -- no real protected endpoint
 * exists yet (POST /auth/staff-users, GET /auth/me land in Tasks 64/65),
 * so this stands in until then.
 */
@RestController
public class TestProtectedController {

    @GetMapping("/test/ping")
    public String ping() {
        return "pong";
    }
}

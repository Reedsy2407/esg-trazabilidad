package pe.esgtrazabilidad.auth.staffuser.service;

import java.time.Instant;
import java.util.UUID;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    private static final String SECRET = "jwt-issuer-test-secret-at-least-32-bytes-long!!";

    private final JwtIssuer jwtIssuer = new JwtIssuer(SECRET);

    @Test
    void issuesATokenWithTheSubjectEmailRoleAndAOneHourExpiry() throws Exception {
        UUID staffUserId = UUID.randomUUID();
        Instant before = Instant.now();

        String token = jwtIssuer.issue(staffUserId, "ana@esgtrazabilidad.pe", StaffRole.ADMIN, false);

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo(staffUserId.toString());
        assertThat(signedJwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo("ana@esgtrazabilidad.pe");
        assertThat(signedJwt.getJWTClaimsSet().getStringClaim("role")).isEqualTo("ADMIN");
        Instant expiry = signedJwt.getJWTClaimsSet().getExpirationTime().toInstant();
        assertThat(expiry).isAfter(before.plusSeconds(3500)).isBefore(before.plusSeconds(3700));
    }

    @Test
    void theChangePasswordFlagIsInTheTokenOnlyWhileThePersonStillHasToChangeIt() throws Exception {
        String pending = jwtIssuer.issue(UUID.randomUUID(), "ana@esgtrazabilidad.pe", StaffRole.STAFF, true);
        String done = jwtIssuer.issue(UUID.randomUUID(), "ana@esgtrazabilidad.pe", StaffRole.STAFF, false);

        assertThat(SignedJWT.parse(pending).getJWTClaimsSet().getBooleanClaim("pwdChange")).isTrue();
        assertThat(SignedJWT.parse(done).getJWTClaimsSet().getClaim("pwdChange")).isNull();
    }
}

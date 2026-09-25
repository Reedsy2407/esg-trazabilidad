package pe.esgtrazabilidad.auth.staffuser.service;

import java.time.Instant;
import java.util.UUID;

import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtIssuerTest {

    private static final String SECRET = "jwt-issuer-test-secret-at-least-32-bytes-long!!";

    private final JwtIssuer jwtIssuer = new JwtIssuer(SECRET);

    @Test
    void issuesATokenWithTheSubjectEmailAndAOneHourExpiry() throws Exception {
        UUID staffUserId = UUID.randomUUID();
        Instant before = Instant.now();

        String token = jwtIssuer.issue(staffUserId, "ana@esgtrazabilidad.pe");

        SignedJWT signedJwt = SignedJWT.parse(token);
        assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo(staffUserId.toString());
        assertThat(signedJwt.getJWTClaimsSet().getStringClaim("email")).isEqualTo("ana@esgtrazabilidad.pe");
        Instant expiry = signedJwt.getJWTClaimsSet().getExpirationTime().toInstant();
        assertThat(expiry).isAfter(before.plusSeconds(3500)).isBefore(before.plusSeconds(3700));
    }
}

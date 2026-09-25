package pe.esgtrazabilidad.auth.staffuser.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The only class in the whole reactor that holds the SIGNING side of
 * JWT_SECRET -- every other service (including auth-service's own
 * SecurityConfig, via shared-kernel's JwtSecurityConfig) only ever
 * verifies. 1-hour expiry, no refresh token -- see SPEC-auth-service.md's
 * Resolved Decisions.
 */
@Component
class JwtIssuer {

    private static final long EXPIRY_HOURS = 1;

    private final String secret;

    JwtIssuer(@Value("${JWT_SECRET}") String secret) {
        this.secret = secret;
    }

    String issue(UUID staffUserId, String email) {
        try {
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(staffUserId.toString())
                    .claim("email", email)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(EXPIRY_HOURS, ChronoUnit.HOURS)))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT", exception);
        }
    }
}

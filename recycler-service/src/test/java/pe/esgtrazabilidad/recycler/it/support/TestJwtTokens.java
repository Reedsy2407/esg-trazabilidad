package pe.esgtrazabilidad.recycler.it.support;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * Shared per-service test-token helper (one per retrofitted service, same
 * shape expected in collection-service/reporting-service's own retrofits) --
 * mints a locally-signed token against a known test secret, no live
 * auth-service HTTP call. Legitimate here specifically because this
 * service only ever VALIDATES tokens offline (NimbusJwtDecoder against the
 * same shared secret) -- see SPEC-auth-service.md's Resolved Decisions for
 * why this differs from auth-service's own IT suite, which uses real
 * /auth/login calls since the login endpoint IS what's under test there.
 */
public final class TestJwtTokens {

    public static final String TEST_SECRET = "recycler-service-it-test-secret-at-least-32-bytes!!";

    private TestJwtTokens() {
    }

    public static String validToken() {
        return signToken(TEST_SECRET, "01a0d8cb-f219-72a2-b006-5150f0e0d685");
    }

    public static String signToken(String secret, String subject) {
        try {
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT de prueba", exception);
        }
    }
}

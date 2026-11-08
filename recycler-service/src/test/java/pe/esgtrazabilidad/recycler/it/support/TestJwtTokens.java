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

    private static final String SUBJECT = "01a0d8cb-f219-72a2-b006-5150f0e0d685";

    private TestJwtTokens() {
    }

    public static String validToken() {
        return signToken(TEST_SECRET, SUBJECT);
    }

    /** A valid ADMIN token (same subject as {@link #validToken()}). */
    public static String adminToken() {
        return sign(TEST_SECRET, SUBJECT, "ADMIN");
    }

    public static String signToken(String secret, String subject) {
        return sign(secret, subject, "STAFF");
    }

    /** A RECYCLER token as auth-service will issue it (A.1), consent and location accepted. */
    public static String recyclerToken() {
        return recyclerToken(60_000);
    }

    /** The same RECYCLER token, already expired beyond the decoder's clock skew. */
    public static String expiredRecyclerToken() {
        return recyclerToken(-120_000);
    }

    private static String sign(String secret, String subject, String role) {
        return sign(secret, new JWTClaimsSet.Builder()
                .subject(subject)
                .claim("role", role)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build());
    }

    private static String recyclerToken(long validMillis) {
        return sign(TEST_SECRET, new JWTClaimsSet.Builder()
                .subject("01a0d8cb-f219-72a2-b006-5150f0e0d601")
                .claim("role", "RECYCLER")
                .claim("rid", "01a0d8cb-f219-72a2-b006-5150f0e0d602")
                .claim("aid", "01a0d8cb-f219-72a2-b006-5150f0e0d603")
                .claim("did", "01a0d8cb-f219-72a2-b006-5150f0e0d604")
                .claim("cv", 1)
                .claim("lc", true)
                .expirationTime(new Date(System.currentTimeMillis() + validMillis))
                .build());
    }

    private static String sign(String secret, JWTClaimsSet claims) {
        try {
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT de prueba", exception);
        }
    }
}

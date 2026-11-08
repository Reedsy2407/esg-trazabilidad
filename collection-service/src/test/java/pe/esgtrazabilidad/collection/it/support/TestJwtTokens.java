package pe.esgtrazabilidad.collection.it.support;

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
 * shape as recycler-service's and reporting-service's own copies) --
 * mints a locally-signed token against a known test secret, no live
 * auth-service HTTP call. Legitimate here specifically because this
 * service only ever VALIDATES tokens offline (NimbusJwtDecoder against the
 * same shared secret) -- see SPEC-auth-service.md's Resolved Decisions for
 * why this differs from auth-service's own IT suite, which uses real
 * /auth/login calls since the login endpoint IS what's under test there.
 */
public final class TestJwtTokens {

    /** The email claim every test token carries, as auth-service's real tokens do. */
    public static final String TEST_EMAIL = "staff-it@example.org";

    public static final String TEST_SECRET = "collection-service-it-test-secret-at-least-32-bytes!";

    /** The subject (actor id) claim every token from {@link #validToken()} carries. */
    public static final String SUBJECT = "01a0d8cb-f219-72a2-b006-5150f0e0d685";

    private TestJwtTokens() {
    }

    public static String validToken() {
        return signToken(TEST_SECRET, SUBJECT);
    }

    public static String signToken(String secret, String subject) {
        try {
            MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(subject)
                    .claim("email", TEST_EMAIL)
                    .claim("role", "STAFF")
                    .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT de prueba", exception);
        }
    }

    /** A RECYCLER token as auth-service will issue it (A.1), consent and location accepted. */
    public static String recyclerToken() {
        return recyclerToken(60_000);
    }

    /** The same RECYCLER token, already expired beyond the decoder's clock skew. */
    public static String expiredRecyclerToken() {
        return recyclerToken(-120_000);
    }

    private static String recyclerToken(long validMillis) {
        try {
            MACSigner signer = new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject("01a0d8cb-f219-72a2-b006-5150f0e0d601")
                    .claim("role", "RECYCLER")
                    .claim("rid", "01a0d8cb-f219-72a2-b006-5150f0e0d602")
                    .claim("aid", "01a0d8cb-f219-72a2-b006-5150f0e0d603")
                    .claim("did", "01a0d8cb-f219-72a2-b006-5150f0e0d604")
                    .claim("cv", 1)
                    .claim("lc", true)
                    .expirationTime(new Date(System.currentTimeMillis() + validMillis))
                    .build();
            SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            signedJwt.sign(signer);
            return signedJwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT de prueba", exception);
        }
    }
}

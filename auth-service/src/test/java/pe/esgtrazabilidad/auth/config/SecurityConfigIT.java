package pe.esgtrazabilidad.auth.config;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.restassured.RestAssured;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

/**
 * Proves the security perimeter itself, end to end over real HTTP -- not
 * any specific business endpoint (none exist yet; Tasks 63-65 add those).
 * TestProtectedController (test-only) stands in for "any endpoint not on
 * the permitAll list."
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=security-config-it-test-secret-32-bytes-min!!")
class SecurityConfigIT {

    private static final String SECRET = "security-config-it-test-secret-32-bytes-min!!";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    @Test
    void swaggerUiIsReachableWithoutAToken() {
        given().redirects().follow(false).when().get("/swagger-ui/index.html").then().statusCode(200);
    }

    @Test
    void loginPathIsNotBlockedBySecurity() {
        // No real /auth/login controller exists yet (Task 63) -- a 404 here
        // (not 401) is exactly what proves permitAll let the request reach
        // MVC dispatch instead of being rejected by the security filter chain.
        given().when().post("/auth/login").then().statusCode(404);
    }

    @Test
    void aNonPermitAllEndpointReturns401WithNoToken() {
        given().when()
                .get("/test/ping")
                .then()
                .statusCode(401)
                .contentType("application/problem+json")
                .body("code", equalTo("AUTH-000"));
    }

    @Test
    void aNonPermitAllEndpointReturns401WithATokenSignedByTheWrongSecret() throws Exception {
        String wrongToken = signToken("a-totally-different-wrong-secret-value-here-123", "someone");

        given().header("Authorization", "Bearer " + wrongToken)
                .when()
                .get("/test/ping")
                .then()
                .statusCode(401);
    }

    @Test
    void aValidTokenSignedWithTheConfiguredSecretAuthenticatesSuccessfully() throws Exception {
        String validToken = signToken(SECRET, "staff-id-123");

        given().header("Authorization", "Bearer " + validToken)
                .when()
                .get("/test/ping")
                .then()
                .statusCode(200)
                .body(equalTo("pong"));
    }

    private String signToken(String secret, String subject) throws Exception {
        MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(signer);
        return signedJwt.serialize();
    }
}

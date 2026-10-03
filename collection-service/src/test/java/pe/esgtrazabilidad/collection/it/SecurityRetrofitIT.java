package pe.esgtrazabilidad.collection.it;


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

import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * This service's security perimeter over real HTTP. The two required
 * negative checks (per SPEC-auth-service.md's Testing Strategy): no token,
 * and a token signed with the wrong secret, both against a real,
 * already-existing business endpoint -- plus the public side of the
 * perimeter: Swagger UI (both URLs), /actuator/health and
 * /actuator/health/liveness must answer without a token, while
 * /actuator/health/readiness must not. Deliberately its own small class,
 * not folded into CompanyApiIT/etc. -- unlike those classes, this one
 * must NOT set a default valid-token request spec in @BeforeEach.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "JWT_SECRET=" + TestJwtTokens.TEST_SECRET,
            "RENDER_GIT_COMMIT=" + SecurityRetrofitIT.DEPLOYED_COMMIT,
            "CORS_ALLOWED_ORIGINS=" + SecurityRetrofitIT.ALLOWED_ORIGIN
        })
class SecurityRetrofitIT {

    static final String DEPLOYED_COMMIT = "0123456789abcdef0123456789abcdef01234567";
    static final String ALLOWED_ORIGIN = "http://localhost:4200";

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
        RestAssuredSetup.anonymous(port);
    }

    @Test
    void aBusinessEndpointWithNoTokenReturns401() {
        given().when().get("/companies").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void aBusinessEndpointWithATokenSignedByTheWrongSecretReturns401() {
        String wrongToken = TestJwtTokens.signToken("a-totally-different-wrong-secret-value-here-123", "someone");

        given().header("Authorization", "Bearer " + wrongToken)
                .when()
                .get("/companies")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-000"));
    }

    @Test
    void swaggerUiRemainsReachableWithoutAToken() {
        given().redirects().follow(false).when().get("/swagger-ui/index.html").then().statusCode(200);
    }

    @Test
    void theSwaggerUiEntryPointRedirectsWithoutAToken() {
        given().redirects().follow(false).when().get("/swagger-ui.html").then().statusCode(302);
    }

    @Test
    void actuatorHealthIsReachableWithoutAToken() {
        // Security must let it through and the actuator must answer. The
        // aggregate status itself can be DOWN (503) here: this IT has no
        // RabbitMQ container for the AMQP health indicator. The real UP check
        // runs in e2e-tests against all four services with a live broker.
        given().when()
                .get("/actuator/health")
                .then()
                .statusCode(anyOf(equalTo(200), equalTo(503)))
                .body("status", notNullValue());
    }

    @Test
    void livenessIsUpWithoutATokenEvenWithNoRabbitMq() {
        // This is Render's health check path (SPEC-deployment.md). Liveness only
        // contains livenessState, so a broker outage -- which this Rabbit-less
        // context simulates -- must not fail it. Exactly 200, unlike the
        // aggregate's "200 or 503" above (which can't pin 503: a locally
        // running compose broker on localhost:5672 would make it UP).
        given().when()
                .get("/actuator/health/liveness")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    void readinessStillRequiresAToken() {
        // The permit is the exact liveness path, not /actuator/health/**.
        given().when().get("/actuator/health/readiness").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void actuatorInfoExposesOnlyTheDeployedCommitWithoutAToken() {
        // scripts/verify-deploy.sh compares this to the commit CI passed. Every
        // built-in info contributor is off, so nothing else may appear here.
        given().when()
                .get("/actuator/info")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("commit", equalTo(DEPLOYED_COMMIT));
    }

    @Test
    void theRestOfTheActuatorStaysBehindAToken() {
        // The info permit is the exact path: exposing info must not open /actuator/**.
        given().when().get("/actuator/env").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void aPreflightFromAnAllowedOriginGetsTheCorsHeadersAndNoAuthChallenge() {
        given().header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .when()
                .options("/companies")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Allow-Methods", containsString("POST"))
                .header("Access-Control-Max-Age", "3600");
    }

    @Test
    void a401FromAnAllowedOriginStillCarriesTheCorsHeaders() {
        // Without them the browser hides the 401 from the frontend entirely.
        given().header("Origin", ALLOWED_ORIGIN)
                .when()
                .get("/companies")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-000"))
                .header("Access-Control-Allow-Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Expose-Headers", containsString("Retry-After"));
    }
}

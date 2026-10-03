package pe.esgtrazabilidad.auth.it;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * POST /auth/login's per-client-IP limit over real HTTP (SPEC-deployment.md).
 * src/test/resources raises the capacity for every other IT (they all log in
 * from 127.0.0.1); this class pins it back to the production value, 5.
 * Each test uses its own client IP through the forwarding header, so the
 * buckets of one test never leak into another inside the shared context.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
        properties = {
            "JWT_SECRET=login-rate-limit-it-test-secret-32-bytes-min!!",
            "esg.auth.login-rate-limit.capacity=5",
            "CORS_ALLOWED_ORIGINS=http://localhost:4200"
        })
class LoginRateLimitIT {

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

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.reset();
        RestAssured.port = port;
    }

    private RequestSpecification from(String clientIp) {
        return given().header("X-Forwarded-For", clientIp);
    }

    private void wrongPasswordLoginFrom(String clientIp, int expectedStatus) {
        from(clientIp).contentType("application/json")
                .body("{\"email\": \"limite@esgtrazabilidad.pe\", \"password\": \"wrong-password\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(expectedStatus);
    }

    @Test
    void fiveWrongPasswordLoginsGet401AndTheSixthFromTheSameIpGets429WithRetryAfter() {
        staffUserRepository.save(
                StaffUser.create("limite@esgtrazabilidad.pe", passwordEncoder.encode("the-real-password"), "Limite"));

        for (int attempt = 1; attempt <= 5; attempt++) {
            from("203.0.113.10").contentType("application/json")
                    .body("{\"email\": \"limite@esgtrazabilidad.pe\", \"password\": \"wrong-password\"}")
                    .when()
                    .post("/auth/login")
                    .then()
                    .statusCode(401)
                    .body("code", equalTo("AUTH-001"));
        }

        from("203.0.113.10").contentType("application/json")
                .body("{\"email\": \"limite@esgtrazabilidad.pe\", \"password\": \"wrong-password\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(429)
                .contentType("application/problem+json")
                .header("Retry-After", notNullValue())
                .body("code", equalTo("AUTH-004"))
                .body("detail", equalTo("Demasiados intentos de inicio de sesión, intenta más tarde"))
                .body("status", equalTo(429));
    }

    @Test
    void a429FromAnAllowedOriginCarriesTheCorsHeadersSoTheBrowserCanReadRetryAfter() {
        // CORS runs before the rate limiter, so even its 429 is readable cross-origin.
        for (int attempt = 1; attempt <= 5; attempt++) {
            wrongPasswordLoginFrom("203.0.113.60", 401);
        }

        from("203.0.113.60").header("Origin", "http://localhost:4200")
                .contentType("application/json")
                .body("{\"email\": \"limite@esgtrazabilidad.pe\", \"password\": \"wrong-password\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(429)
                .body("code", equalTo("AUTH-004"))
                .header("Retry-After", notNullValue())
                .header("Access-Control-Allow-Origin", "http://localhost:4200")
                .header("Access-Control-Expose-Headers", containsString("Retry-After"));
    }

    @Test
    void aCorrectPasswordIsRefusedTooOnceTheIpIsExhaustedSoTheLimitCountsEveryAttempt() {
        staffUserRepository.save(
                StaffUser.create("exhausta@esgtrazabilidad.pe", passwordEncoder.encode("the-real-password"), "Exhausta"));
        for (int attempt = 1; attempt <= 5; attempt++) {
            wrongPasswordLoginFrom("203.0.113.20", 401);
        }

        from("203.0.113.20").contentType("application/json")
                .body("{\"email\": \"exhausta@esgtrazabilidad.pe\", \"password\": \"the-real-password\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(429)
                .body("code", equalTo("AUTH-004"));
    }

    @Test
    void anotherClientIpIsStillAllowedWhileOneIsExhausted() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            wrongPasswordLoginFrom("203.0.113.30", 401);
        }
        wrongPasswordLoginFrom("203.0.113.30", 429);

        wrongPasswordLoginFrom("198.51.100.30", 401);
    }

    @Test
    void theLimitDoesNotApplyToTheOtherAuthEndpoints() {
        staffUserRepository.save(
                StaffUser.create("otra@esgtrazabilidad.pe", passwordEncoder.encode("password-otra-123"), "Otra"));
        String token = from("203.0.113.40").contentType("application/json")
                .body("{\"email\": \"otra@esgtrazabilidad.pe\", \"password\": \"password-otra-123\"}")
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
        for (int attempt = 1; attempt <= 4; attempt++) {
            wrongPasswordLoginFrom("203.0.113.40", 401);
        }
        wrongPasswordLoginFrom("203.0.113.40", 429);

        // Same exhausted IP: /auth/me and /auth/staff-users still answer normally.
        for (int call = 1; call <= 6; call++) {
            from("203.0.113.40").header("Authorization", "Bearer " + token)
                    .when()
                    .get("/auth/me")
                    .then()
                    .statusCode(200)
                    .body("email", equalTo("otra@esgtrazabilidad.pe"));
        }
        from("203.0.113.40").header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"email": "sin-limite@esgtrazabilidad.pe", "password": "password-sin-limite", "fullName": "Sin Limite"}
                        """)
                .when()
                .post("/auth/staff-users")
                .then()
                .statusCode(201);
    }
}

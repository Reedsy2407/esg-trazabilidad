package pe.esgtrazabilidad.auth.it;

import io.restassured.RestAssured;

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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=auth-api-it-test-secret-at-least-32-bytes!!")
class AuthApiIT {

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
        RestAssured.port = port;
    }

    private void registerStaffUser(String email, String rawPassword) {
        registerStaffUser(email, rawPassword, "Usuario de prueba IT");
    }

    private void registerStaffUser(String email, String rawPassword, String fullName) {
        staffUserRepository.save(StaffUser.create(email, passwordEncoder.encode(rawPassword), fullName));
    }

    private String loginAndGetToken(String email, String rawPassword) {
        return given().contentType("application/json")
                .body("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, rawPassword))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    @Test
    void loginWithValidCredentialsReturnsARealJwt() {
        registerStaffUser("valido@esgtrazabilidad.pe", "correct-password-123");

        given().contentType("application/json")
                .body("""
                        {"email": "valido@esgtrazabilidad.pe", "password": "correct-password-123"}
                        """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue());
    }

    @Test
    void loginWithADifferentlyCasedEmailThanTheOneRegisteredStillSucceeds() {
        registerStaffUser("mayusculas@esgtrazabilidad.pe", "correct-password-123");

        given().contentType("application/json")
                .body("""
                        {"email": "MAYUSCULAS@ESGtrazabilidad.pe", "password": "correct-password-123"}
                        """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue());
    }

    @Test
    void loginWithAnUnknownEmailReturnsAuth001() {
        given().contentType("application/json")
                .body("""
                        {"email": "no-existe@esgtrazabilidad.pe", "password": "cualquiera"}
                        """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-001"));
    }

    @Test
    void loginWithTheWrongPasswordReturnsTheSameAuth001() {
        registerStaffUser("existe@esgtrazabilidad.pe", "the-real-password");

        given().contentType("application/json")
                .body("""
                        {"email": "existe@esgtrazabilidad.pe", "password": "wrong-password"}
                        """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-001"));
    }

    @Test
    void loginWithABlankPasswordFailsValidationBeforeReachingTheService() {
        given().contentType("application/json")
                .body("""
                        {"email": "valido@esgtrazabilidad.pe", "password": ""}
                        """)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void meWithoutATokenReturns401() {
        given().when().get("/auth/me").then().statusCode(401);
    }

    @Test
    void meReturnsTheIdentityOfTheAuthenticatedCallerNotAnyoneElses() {
        registerStaffUser("primera@esgtrazabilidad.pe", "password-primera-123", "Primera Persona");
        registerStaffUser("segunda@esgtrazabilidad.pe", "password-segunda-123", "Segunda Persona");
        String tokenForSegunda = loginAndGetToken("segunda@esgtrazabilidad.pe", "password-segunda-123");

        given().header("Authorization", "Bearer " + tokenForSegunda)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo("segunda@esgtrazabilidad.pe"))
                .body("fullName", equalTo("Segunda Persona"))
                .body("active", equalTo(true));
    }
}

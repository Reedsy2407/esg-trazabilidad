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
@TestPropertySource(properties = "JWT_SECRET=staff-user-api-it-test-secret-32-bytes-min!!")
class StaffUserApiIT {

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

    /** A real login, not a hand-minted token -- proves the actual issued-token/protected-endpoint path together. */
    private String loginAndGetToken(String email, String rawPassword) {
        staffUserRepository.save(StaffUser.create(email, passwordEncoder.encode(rawPassword), "Autora del login"));
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
    void creatingAStaffUserWithoutATokenReturns401() {
        given().contentType("application/json")
                .body("""
                        {"email": "nueva@esgtrazabilidad.pe", "password": "algo-largo", "fullName": "Nueva Persona"}
                        """)
                .when()
                .post("/auth/staff-users")
                .then()
                .statusCode(401);
    }

    @Test
    void anAuthenticatedStaffMemberCanCreateAnotherStaffAccount() {
        String token = loginAndGetToken("autora@esgtrazabilidad.pe", "password-autora-123");

        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"email": "onboarded@esgtrazabilidad.pe", "password": "password-onboarded-123", "fullName": "Persona Onboardeada"}
                        """)
                .when()
                .post("/auth/staff-users")
                .then()
                .statusCode(201)
                .body("email", equalTo("onboarded@esgtrazabilidad.pe"))
                .body("fullName", equalTo("Persona Onboardeada"))
                .body("active", equalTo(true))
                .body("id", notNullValue());
    }

    @Test
    void creatingAStaffUserWithAnEmailThatAlreadyExistsReturnsAuth002() {
        String token = loginAndGetToken("autora2@esgtrazabilidad.pe", "password-autora2-123");
        staffUserRepository.save(
                StaffUser.create("duplicada@esgtrazabilidad.pe", passwordEncoder.encode("cualquiera"), "Ya existe"));

        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body("""
                        {"email": "duplicada@esgtrazabilidad.pe", "password": "otra-password", "fullName": "Otra Persona"}
                        """)
                .when()
                .post("/auth/staff-users")
                .then()
                .statusCode(409)
                .body("code", equalTo("AUTH-002"));
    }
}

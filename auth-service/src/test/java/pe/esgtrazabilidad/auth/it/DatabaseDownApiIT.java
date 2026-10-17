package pe.esgtrazabilidad.auth.it;

import java.util.Map;

import io.restassured.RestAssured;

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

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

/**
 * The service while its database is down (the "503 DOWN" case): a real Postgres is stopped under a
 * running service. A request that needs the database answers 503 with the stable code and Retry-After
 * (never a raw 500, never the driver's message), the aggregate health is DOWN (503) so a monitor sees
 * it, and the liveness probe stays UP so the platform does not restart a healthy JVM for a provider outage.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=database-down-api-it-test-secret-32-bytes!!")
class DatabaseDownApiIT {

    private static final String PASSWORD = "una-clave-larga-y-segura";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // The pool waits 2 s, not the default 30, for a connection that will never come.
        registry.add("spring.datasource.hikari.connection-timeout", () -> "2000");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private StaffUserRepository staffUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void whenTheDatabaseGoesDownRequestsAnswer503WithACodeAndRetryAfterAndLivenessStaysUp() {
        RestAssured.port = port;
        staffUsers.save(StaffUser.create("caida@esgtrazabilidad.pe", passwordEncoder.encode(PASSWORD), "Caída", StaffRole.STAFF, false));
        given().contentType("application/json")
                .body(Map.of("email", "caida@esgtrazabilidad.pe", "password", PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(200);

        postgres.stop();

        String body = given().contentType("application/json")
                .body(Map.of("email", "caida@esgtrazabilidad.pe", "password", PASSWORD))
                .post("/auth/login")
                .then()
                .statusCode(503)
                .body("code", equalTo("SERVICE_UNAVAILABLE"))
                .header("Retry-After", equalTo("10"))
                .body("detail", not(containsString("jdbc")))
                .extract()
                .asString();
        assertThat(body).doesNotContain("postgres", "Connection", "SQL", "localhost");

        given().get("/actuator/health").then().statusCode(503);
        given().get("/actuator/health/liveness").then().statusCode(200).body("status", equalTo("UP"));
    }
}

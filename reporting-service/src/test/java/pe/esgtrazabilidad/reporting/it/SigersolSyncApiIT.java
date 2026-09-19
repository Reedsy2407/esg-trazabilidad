package pe.esgtrazabilidad.reporting.it;

import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SigersolSyncApiIT {

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

    private String registerRequest(UUID associationId, String periodStart, String periodEnd) {
        return """
                {
                  "associationId": "%s",
                  "periodStart": "%s",
                  "periodEnd": "%s",
                  "hierarchyCompliancePercent": 85.5,
                  "officialKilosDeclared": 1200.00,
                  "sourceNote": "Declaración manual de prueba IT"
                }
                """.formatted(associationId, periodStart, periodEnd);
    }

    @Test
    void registerGetAndListASigersolSync() {
        UUID associationId = UUID.randomUUID();
        String id = given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-01-01", "2026-01-31"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201)
                .body("associationId", equalTo(associationId.toString()))
                .body("hierarchyCompliancePercent", equalTo(85.5f))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/sigersol-syncs/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));

        given()
                .when()
                .get("/sigersol-syncs")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void registeringAnOverlappingPeriodForTheSameAssociationReturnsConflict() {
        UUID associationId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-02-01", "2026-02-28"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-02-15", "2026-03-15"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-006"));
    }

    @Test
    void registeringANonOverlappingAdjacentPeriodForTheSameAssociationSucceeds() {
        // Exercises the real register() -> existsOverlapping() -> save() path
        // (this task's own new JPQL check), not just the direct-repository
        // EXCLUDE-constraint IT from Task 44 -- a regression in
        // existsOverlapping()'s own boundary condition (e.g. a flipped
        // <=/< operator) would pass every other test in this class but
        // would show up here as an unexpected 409.
        UUID associationId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-05-01", "2026-05-31"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-06-01", "2026-06-30"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);
    }

    @Test
    void registeringAPeriodSharingExactlyOneBoundaryDayReturnsConflict() {
        // The same decisive boundary case Task 44 proved at the direct-
        // repository level (daterange's inclusive upper bound), now proven
        // through the full register() -> existsOverlapping() service path.
        UUID associationId = UUID.randomUUID();
        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-07-01", "2026-07-31"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-07-31", "2026-08-31"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-006"));
    }

    @Test
    void gettingAMissingSigersolSyncReturnsNotFound() {
        given()
                .when()
                .get("/sigersol-syncs/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-007"));
    }

    @Test
    void registeringAnInvalidPeriodReturnsATypedBadRequest() {
        given()
                .contentType(ContentType.JSON)
                .body(registerRequest(UUID.randomUUID(), "2026-03-31", "2026-03-01"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(400)
                .body("code", equalTo("RPT-008"));
    }

    @Test
    void registeringANegativeOfficialKilosDeclaredReturnsAValidationError() {
        String requestWithNegativeKilos = """
                {
                  "associationId": "%s",
                  "periodStart": "2026-09-01",
                  "periodEnd": "2026-09-30",
                  "hierarchyCompliancePercent": 50,
                  "officialKilosDeclared": -1
                }
                """.formatted(UUID.randomUUID());

        given()
                .contentType(ContentType.JSON)
                .body(requestWithNegativeKilos)
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void registeringAnOutOfRangeCompliancePercentReturnsAValidationError() {
        String requestWithBadPercent = """
                {
                  "associationId": "%s",
                  "periodStart": "2026-04-01",
                  "periodEnd": "2026-04-30",
                  "hierarchyCompliancePercent": 150
                }
                """.formatted(UUID.randomUUID());

        given()
                .contentType(ContentType.JSON)
                .body(requestWithBadPercent)
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }
}

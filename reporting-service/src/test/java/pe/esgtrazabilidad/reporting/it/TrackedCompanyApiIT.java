package pe.esgtrazabilidad.reporting.it;

import java.util.UUID;

import io.restassured.http.ContentType;

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

import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class TrackedCompanyApiIT {

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
        RestAssuredSetup.authenticated(port);
    }

    private String registerTrackedCompanyRequest(String ruc, UUID associationId) {
        return """
                {
                  "name": "Empresa de prueba IT",
                  "ruc": "%s",
                  "associationId": "%s"
                }
                """.formatted(ruc, associationId);
    }

    @Test
    void registerGetAndListATrackedCompany() {
        UUID associationId = UUID.randomUUID();
        String id = given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20111111111", associationId))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .body("ruc", equalTo("20111111111"))
                .body("associationId", equalTo(associationId.toString()))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/tracked-companies/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("ruc", equalTo("20111111111"));

        given()
                .when()
                .get("/tracked-companies")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void registeringWithADuplicateRucReturnsConflict() {
        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20222222222", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20222222222", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-002"));
    }

    @Test
    void gettingAMissingTrackedCompanyReturnsNotFound() {
        given()
                .when()
                .get("/tracked-companies/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-001"));
    }

    @Test
    void registeringWithANonNumericRucReturnsAValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("1234567890a", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/tracked-companies")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}

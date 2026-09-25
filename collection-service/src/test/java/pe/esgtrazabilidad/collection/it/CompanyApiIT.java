package pe.esgtrazabilidad.collection.it;

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

import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class CompanyApiIT {

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

    private String createCompanyRequest(String ruc) {
        return """
                {
                  "name": "Empresa de prueba IT",
                  "ruc": "%s",
                  "contactEmail": "it@test.pe",
                  "contactPhone": "999999999",
                  "address": "Av. Empresarial 456"
                }
                """.formatted(ruc);
    }

    @Test
    void createGetAndListACompany() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createCompanyRequest("20111111111"))
                .when()
                .post("/companies")
                .then()
                .statusCode(201)
                .body("ruc", equalTo("20111111111"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/companies/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("ruc", equalTo("20111111111"));

        given()
                .when()
                .get("/companies")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void creatingWithADuplicateRucReturnsConflict() {
        given()
                .contentType(ContentType.JSON)
                .body(createCompanyRequest("20222222222"))
                .when()
                .post("/companies")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(createCompanyRequest("20222222222"))
                .when()
                .post("/companies")
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-005"));
    }

    @Test
    void gettingAMissingCompanyReturnsNotFound() {
        given()
                .when()
                .get("/companies/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-004"));
    }

    @Test
    void creatingWithANonNumericRucReturnsAValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(createCompanyRequest("1234567890a"))
                .when()
                .post("/companies")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void creatingWithAnInvalidEmailReturnsAValidationError() {
        String requestWithInvalidEmail = """
                {
                  "name": "Empresa de prueba IT",
                  "ruc": "20333333333",
                  "contactEmail": "not-an-email",
                  "contactPhone": "999999999",
                  "address": "Av. Empresarial 456"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithInvalidEmail)
                .when()
                .post("/companies")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/companies")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}

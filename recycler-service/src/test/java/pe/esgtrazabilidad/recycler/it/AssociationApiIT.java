package pe.esgtrazabilidad.recycler.it;

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

import pe.esgtrazabilidad.recycler.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.recycler.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class AssociationApiIT {

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

    // Every request in this class needs a token now that SecurityConfig
    // requires authentication by default -- setting the default spec once
    // here means none of the existing given() call sites below need
    // touching individually. RestAssuredSetup resets RestAssured's static
    // state first; see its javadoc for why that reset is required.
    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
    }

    private String createAssociationRequest(String ruc) {
        return """
                {
                  "name": "Asociación de prueba IT",
                  "ruc": "%s",
                  "registrationNumber": "REG-IT-001",
                  "address": "Dirección de prueba",
                  "contactEmail": "it@test.pe",
                  "contactPhone": "999999999"
                }
                """.formatted(ruc);
    }

    @Test
    void createGetAndListAnAssociation() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("20111111111"))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .body("ruc", equalTo("20111111111"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/associations/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("ruc", equalTo("20111111111"));

        given()
                .when()
                .get("/associations")
                .then()
                .statusCode(200)
                .body("content", hasSize(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void creatingWithADuplicateRucReturnsConflict() {
        given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("20222222222"))
                .when()
                .post("/associations")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("20222222222"))
                .when()
                .post("/associations")
                .then()
                .statusCode(409)
                .body("code", equalTo("ASO-002"));
    }

    @Test
    void gettingAMissingAssociationReturnsNotFound() {
        given()
                .when()
                .get("/associations/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("ASO-001"));
    }

    @Test
    void creatingWithANonNumericRucReturnsAValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("1234567890a"))
                .when()
                .post("/associations")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void creatingWithAnInvalidEmailReturnsAValidationError() {
        String requestWithInvalidEmail = """
                {
                  "name": "Asociación de prueba IT",
                  "ruc": "20333333333",
                  "registrationNumber": "REG-IT-001",
                  "address": "Dirección de prueba",
                  "contactEmail": "not-an-email",
                  "contactPhone": "999999999"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithInvalidEmail)
                .when()
                .post("/associations")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void suspendingAndReactivatingAnAssociationChangesItsStatus() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("20444444444"))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .patch("/associations/{id}/suspend", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUSPENDED"));

        given()
                .when()
                .get("/associations/{id}", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUSPENDED"));

        given()
                .when()
                .patch("/associations/{id}/activate", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void suspendingAnAlreadySuspendedAssociationReturnsConflict() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest("20555555555"))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().when().patch("/associations/{id}/suspend", id).then().statusCode(200);

        given()
                .when()
                .patch("/associations/{id}/suspend", id)
                .then()
                .statusCode(409)
                .body("code", equalTo("ASO-003"));
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/associations")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}

package pe.esgtrazabilidad.collection.it;

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
class NeighborApiIT {

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

    private String createNeighborRequest(String fullName) {
        return """
                {
                  "fullName": "%s",
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "district": "Surco"
                }
                """.formatted(fullName);
    }

    @Test
    void createGetAndListANeighbor() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest("Ana Torres IT"))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .body("fullName", equalTo("Ana Torres IT"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("fullName", equalTo("Ana Torres IT"));

        given()
                .when()
                .get("/neighbors")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void gettingAMissingNeighborReturnsNotFound() {
        given()
                .when()
                .get("/neighbors/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-001"));
    }

    @Test
    void creatingWithoutAnAddressReturnsAValidationError() {
        String requestWithoutAddress = """
                {
                  "fullName": "Ana Torres IT",
                  "phone": "999999999",
                  "district": "Surco"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithoutAddress)
                .when()
                .post("/neighbors")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void creatingWithoutAFullNameReturnsAValidationError() {
        String requestWithoutFullName = """
                {
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "district": "Surco"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithoutFullName)
                .when()
                .post("/neighbors")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/neighbors")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}

package pe.esgtrazabilidad.recycler.it;

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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
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
}

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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecyclerApiIT {

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
                  "name": "Asociación para recicladores IT",
                  "ruc": "%s",
                  "registrationNumber": "REG-IT-100",
                  "address": "Dirección de prueba",
                  "contactEmail": "it@test.pe",
                  "contactPhone": "999999999"
                }
                """.formatted(ruc);
    }

    private String createRecyclerRequest(String dni) {
        return """
                {
                  "fullName": "Juan Pérez",
                  "dni": "%s",
                  "phone": "999999999"
                }
                """.formatted(dni);
    }

    private String createAssociation(String ruc) {
        return given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest(ruc))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void createGetAndListARecycler() {
        String associationId = createAssociation("20444444444");

        String recyclerId = given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("11111111"))
                .when()
                .post("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(201)
                .body("dni", equalTo("11111111"))
                .body("status", equalTo("ACTIVE"))
                .body("associationId", equalTo(associationId))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/associations/{associationId}/recyclers/{id}", associationId, recyclerId)
                .then()
                .statusCode(200)
                .body("id", equalTo(recyclerId))
                .body("dni", equalTo("11111111"));

        given()
                .when()
                .get("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void creatingUnderANonexistentAssociationReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("22222222"))
                .when()
                .post("/associations/{associationId}/recyclers", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("REC-003"));
    }

    @Test
    void creatingWithADuplicateDniReturnsConflict() {
        String associationId = createAssociation("20555555555");

        given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("33333333"))
                .when()
                .post("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("33333333"))
                .when()
                .post("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(409)
                .body("code", equalTo("REC-002"));
    }

    @Test
    void gettingAMissingRecyclerReturnsNotFound() {
        String associationId = createAssociation("20666666666");

        given()
                .when()
                .get(
                        "/associations/{associationId}/recyclers/{id}",
                        associationId,
                        "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("REC-001"));
    }

    @Test
    void gettingARecyclerThroughAnotherAssociationsPathReturnsNotFound() {
        String associationAId = createAssociation("20777777777");
        String associationBId = createAssociation("20888888888");

        given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("44444444"))
                .when()
                .post("/associations/{associationId}/recyclers", associationAId)
                .then()
                .statusCode(201);

        String recyclerBId = given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("55555555"))
                .when()
                .post("/associations/{associationId}/recyclers", associationBId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .get("/associations/{associationId}/recyclers/{id}", associationAId, recyclerBId)
                .then()
                .statusCode(404)
                .body("code", equalTo("REC-001"));
    }

    @Test
    void deactivatingAndReactivatingARecyclerChangesItsStatus() {
        String associationId = createAssociation("20999999999");
        String recyclerId = given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("66666666"))
                .when()
                .post("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .patch("/associations/{associationId}/recyclers/{id}/deactivate", associationId, recyclerId)
                .then()
                .statusCode(200)
                .body("status", equalTo("INACTIVE"));

        given()
                .when()
                .patch("/associations/{associationId}/recyclers/{id}/activate", associationId, recyclerId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void activatingAnAlreadyActiveRecyclerReturnsConflict() {
        String associationId = createAssociation("20101010102");
        String recyclerId = given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("77777788"))
                .when()
                .post("/associations/{associationId}/recyclers", associationId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .patch("/associations/{associationId}/recyclers/{id}/activate", associationId, recyclerId)
                .then()
                .statusCode(409)
                .body("code", equalTo("REC-004"));
    }

    @Test
    void deactivatingARecyclerThroughAnotherAssociationsPathReturnsNotFound() {
        String associationAId = createAssociation("20101010103");
        String associationBId = createAssociation("20101010104");
        String recyclerBId = given()
                .contentType(ContentType.JSON)
                .body(createRecyclerRequest("88888899"))
                .when()
                .post("/associations/{associationId}/recyclers", associationBId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .patch("/associations/{associationId}/recyclers/{id}/deactivate", associationAId, recyclerBId)
                .then()
                .statusCode(404)
                .body("code", equalTo("REC-001"));
    }
}

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
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

/** Deactivate / reactivate a neighbour (v3, brief A + D8), against real Postgres. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class NeighborStatusApiIT {

    // The subject TestJwtTokens.validToken() signs, i.e. who RestAssuredSetup acts as.
    private static final String TOKEN_SUBJECT = "01a0d8cb-f219-72a2-b006-5150f0e0d685";

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

    @Test
    void deactivatingPausesTheActiveSchedulesAndRecordsWhoFromTheTokenNotTheBody() {
        String id = createNeighbor("Desactivar IT");
        createSchedule(id, "MONDAY", "08:00");
        createSchedule(id, "THURSDAY", "10:30");

        given().contentType(ContentType.JSON)
                .body("""
                        {"reason": "Se mudó a otra ciudad", "actorId": "00000000-0000-0000-0000-000000000001"}
                        """)
                .when().patch("/neighbors/{id}/deactivate", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("INACTIVE"));

        given().when().get("/neighbors/{id}/schedules", id).then()
                .statusCode(200)
                .body("content", hasSize(2))
                .body("content.status", everyItem(equalTo("PAUSED")));
        given().when().get("/neighbors/{id}/status-changes", id).then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].fromStatus", equalTo("ACTIVE"))
                .body("[0].toStatus", equalTo("INACTIVE"))
                .body("[0].reason", equalTo("Se mudó a otra ciudad"))
                .body("[0].actorId", equalTo(TOKEN_SUBJECT));
    }

    @Test
    void reactivatingLeavesTheSchedulesPausedAndListsTheHistoryNewestFirst() {
        String id = createNeighbor("Reactivar IT");
        createSchedule(id, "TUESDAY", "07:00");
        changeStatus(id, "deactivate", "Viaje largo").then().statusCode(200);

        changeStatus(id, "activate", "Regresó").then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));

        given().when().get("/neighbors/{id}/schedules", id).then()
                .body("content.status", contains("PAUSED"));
        given().when().get("/neighbors/{id}/status-changes", id).then()
                .body("reason", contains("Regresó", "Viaje largo"));
        given().when().get("/neighbors/{id}", id).then()
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void anEditAfterDeactivatingKeepsTheNeighborInactive() {
        String id = createNeighbor("Editar Inactivo IT");
        changeStatus(id, "deactivate", "Pidió pausa").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"fullName\": \"Editado\", \"address\": \"Jr. 2\", \"districtUbigeo\": \"150112\"}")
                .when().patch("/neighbors/{id}", id)
                .then().statusCode(200).body("status", equalTo("INACTIVE"));
    }

    @Test
    void theSameStatusTwiceIsCol012AndAMissingOrLongReasonIsAValidationError() {
        String id = createNeighbor("Errores Estado IT");

        changeStatus(id, "activate", "Ya activo").then()
                .statusCode(409).body("code", equalTo("COL-012"));
        changeStatus(id, "deactivate", " ").then()
                .statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        changeStatus(id, "deactivate", "x".repeat(501)).then()
                .statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        changeStatus(id, "deactivate", "Primera vez").then().statusCode(200);
        changeStatus(id, "deactivate", "Otra vez").then()
                .statusCode(409).body("code", equalTo("COL-012"));
        changeStatus("00000000-0000-0000-0000-000000000000", "deactivate", "x").then()
                .statusCode(404).body("code", equalTo("COL-001"));
        given().when().get("/neighbors/{id}/status-changes", id).then().body("", hasSize(1));
    }

    private io.restassured.response.Response changeStatus(String id, String action, String reason) {
        return given().contentType(ContentType.JSON)
                .body("{\"reason\": \"%s\"}".formatted(reason))
                .when().patch("/neighbors/{id}/" + action, id);
    }

    private String createNeighbor(String fullName) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"fullName": "%s", "address": "Jr. Prueba 1", "districtUbigeo": "150110"}
                        """.formatted(fullName))
                .when().post("/neighbors")
                .then().statusCode(201)
                .extract().path("id");
    }

    private void createSchedule(String neighborId, String dayOfWeek, String time) {
        given().contentType(ContentType.JSON)
                .body("""
                        {"dayOfWeek": "%s", "time": "%s"}
                        """.formatted(dayOfWeek, time))
                .when().post("/neighbors/{neighborId}/schedules", neighborId)
                .then().statusCode(201);
    }
}

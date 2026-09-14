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
class CollectionScheduleApiIT {

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

    private String createNeighbor(String fullName) {
        return given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest(fullName))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createScheduleRequest(String dayOfWeek, String time) {
        return """
                {
                  "dayOfWeek": "%s",
                  "time": "%s"
                }
                """.formatted(dayOfWeek, time);
    }

    private String createSchedule(String neighborId, String dayOfWeek, String time) {
        return given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest(dayOfWeek, time))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void createGetAndListASchedule() {
        String neighborId = createNeighbor("Ana Torres IT");

        String id = given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("MONDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201)
                .body("dayOfWeek", equalTo("MONDAY"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{neighborId}/schedules/{id}", neighborId, id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("dayOfWeek", equalTo("MONDAY"));

        given()
                .when()
                .get("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void creatingUnderANonexistentNeighborReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("MONDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-001"));
    }

    @Test
    void creatingASecondActiveScheduleForTheSameDayReturnsConflict() {
        String neighborId = createNeighbor("Ana Torres IT 2");

        given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("TUESDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("TUESDAY", "10:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-002"));
    }

    @Test
    void creatingSchedulesOnDifferentDaysForTheSameNeighborBothSucceed() {
        String neighborId = createNeighbor("Ana Torres IT 3");

        given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("WEDNESDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("THURSDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201);
    }

    @Test
    void gettingAMissingScheduleReturnsNotFound() {
        String neighborId = createNeighbor("Ana Torres IT 4");

        given()
                .when()
                .get("/neighbors/{neighborId}/schedules/{id}", neighborId, "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-006"));
    }

    @Test
    void gettingAScheduleThroughAnotherNeighborsPathReturnsNotFound() {
        String neighborAId = createNeighbor("Ana Torres IT 5");
        String neighborBId = createNeighbor("Ana Torres IT 6");

        String scheduleBId = given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest("FRIDAY", "09:00:00"))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborBId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{neighborId}/schedules/{id}", neighborAId, scheduleBId)
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-006"));
    }

    @Test
    void pausingAndReactivatingAScheduleChangesItsStatus() {
        String neighborId = createNeighbor("Ana Torres IT 7");
        String id = createSchedule(neighborId, "SATURDAY", "09:00:00");

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/pause", neighborId, id)
                .then()
                .statusCode(200)
                .body("status", equalTo("PAUSED"));

        given()
                .when()
                .get("/neighbors/{neighborId}/schedules/{id}", neighborId, id)
                .then()
                .statusCode(200)
                .body("status", equalTo("PAUSED"));

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/reactivate", neighborId, id)
                .then()
                .statusCode(200)
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void pausingAnAlreadyPausedScheduleReturnsConflict() {
        String neighborId = createNeighbor("Ana Torres IT 8");
        String id = createSchedule(neighborId, "SUNDAY", "09:00:00");

        given().when().patch("/neighbors/{neighborId}/schedules/{id}/pause", neighborId, id).then().statusCode(200);

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/pause", neighborId, id)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-008"));
    }

    @Test
    void cancellingAScheduleIsTerminal() {
        String neighborId = createNeighbor("Ana Torres IT 9");
        String id = createSchedule(neighborId, "MONDAY", "09:00:00");

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/cancel", neighborId, id)
                .then()
                .statusCode(200)
                .body("status", equalTo("CANCELLED"));

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/pause", neighborId, id)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-008"));

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/reactivate", neighborId, id)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-008"));
    }

    @Test
    void reactivatingIntoANewConflictReturnsConflict() {
        String neighborId = createNeighbor("Ana Torres IT 10");
        String pausedId = createSchedule(neighborId, "TUESDAY", "09:00:00");

        given().when()
                .patch("/neighbors/{neighborId}/schedules/{id}/pause", neighborId, pausedId)
                .then()
                .statusCode(200);

        // Same neighbor, same day -- allowed now, since the first one is PAUSED, not ACTIVE.
        createSchedule(neighborId, "TUESDAY", "10:00:00");

        given()
                .when()
                .patch("/neighbors/{neighborId}/schedules/{id}/reactivate", neighborId, pausedId)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-002"));
    }
}

package pe.esgtrazabilidad.collection.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;

/** Deactivate / reactivate a neighbour (v3, brief A + D8), against real Postgres. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class NeighborStatusApiIT {

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

    @Autowired
    private DataSource dataSource;

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
                        {"reason": "Se mudó a otra ciudad", "actorId": "00000000-0000-0000-0000-000000000001", "actorEmail": "fake@example.org"}
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
                .body("[0].actorId", equalTo(TestJwtTokens.SUBJECT))
                .body("[0].actorEmail", equalTo(TestJwtTokens.TEST_EMAIL));
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

    @Test
    void twoSimultaneousDeactivationsLetExactlyOneThroughAndRecordOneHistoryRow() throws Exception {
        String id = createNeighbor("Carrera IT");
        createSchedule(id, "SATURDAY", "09:00");
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> deactivate = () -> {
                start.await();
                return changeStatus(id, "deactivate", "Carrera").then().extract().statusCode();
            };
            Future<Integer> first = pool.submit(deactivate);
            Future<Integer> second = pool.submit(deactivate);
            start.countDown();

            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }

        given().when().get("/neighbors/{id}/status-changes", id).then().body("", hasSize(1));
        given().when().get("/neighbors/{id}", id).then().body("status", equalTo("INACTIVE"));
        given().when().get("/neighbors/{id}/schedules", id).then().body("content.status", contains("PAUSED"));
    }

    @Test
    void aNewScheduleWaitsForAnUncommittedDeactivationAndThenGetsCol013() throws Exception {
        String id = createNeighbor("Bloqueo FOR SHARE IT");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection deactivation = dataSource.getConnection()) {
            // A deactivation in flight: the status UPDATE is done, the commit isn't.
            deactivation.setAutoCommit(false);
            try (PreparedStatement update = deactivation.prepareStatement(
                    "UPDATE neighbor SET status = 'INACTIVE' WHERE id = ?::uuid AND status = 'ACTIVE'")) {
                update.setString(1, id);
                assertThat(update.executeUpdate()).isEqualTo(1);
            }

            Future<io.restassured.response.Response> newSchedule = pool.submit(() -> given()
                    .contentType(ContentType.JSON)
                    .body("{\"dayOfWeek\": \"SUNDAY\", \"time\": \"10:00\"}")
                    .when().post("/neighbors/{neighborId}/schedules", id));

            // Without FOR SHARE the request reads the committed ACTIVE row and
            // answers 201 right away; with it, it waits for the deactivation.
            assertThatThrownBy(() -> newSchedule.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            deactivation.commit();

            io.restassured.response.Response response = newSchedule.get(30, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.jsonPath().getString("code")).isEqualTo("COL-013");
        } finally {
            pool.shutdownNow();
        }
        given().when().get("/neighbors/{id}/schedules", id).then().body("content", hasSize(0));
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

    private String createSchedule(String neighborId, String dayOfWeek, String time) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"dayOfWeek": "%s", "time": "%s"}
                        """.formatted(dayOfWeek, time))
                .when().post("/neighbors/{neighborId}/schedules", neighborId)
                .then().statusCode(201)
                .extract().path("id");
    }

    @Test
    void anInactiveNeighborGetsNoNewScheduleNoReactivatedScheduleAndNoNewCollection() {
        String id = createNeighbor("Inactivo COL-013 IT");
        String scheduleId = createSchedule(id, "WEDNESDAY", "08:00");
        changeStatus(id, "deactivate", "Pausa").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"dayOfWeek\": \"FRIDAY\", \"time\": \"09:00\"}")
                .when().post("/neighbors/{neighborId}/schedules", id)
                .then().statusCode(409).body("code", equalTo("COL-013"));
        given().when().patch("/neighbors/{neighborId}/schedules/{sid}/reactivate", id, scheduleId)
                .then().statusCode(409).body("code", equalTo("COL-013"));
        given().contentType(ContentType.JSON)
                .body("""
                        {"associationId": "%s", "collectionDate": "2026-09-07", "weightKg": 5.5}
                        """.formatted(UUID.randomUUID()))
                .when().post("/neighbors/{neighborId}/collection-records", id)
                .then().statusCode(409).body("code", equalTo("COL-013"));
        given().when().get("/neighbors/{id}/schedules", id).then()
                .body("content", hasSize(1))
                .body("content.status", contains("PAUSED"));
        given().queryParam("from", "2026-09-01").queryParam("to", "2026-09-30")
                .when().get("/neighbors/{neighborId}/collection-records", id)
                .then().statusCode(200).body("content", hasSize(0));

        changeStatus(id, "activate", "Volvió").then().statusCode(200);
        given().when().patch("/neighbors/{neighborId}/schedules/{sid}/reactivate", id, scheduleId)
                .then().statusCode(200).body("status", equalTo("ACTIVE"));
    }
}

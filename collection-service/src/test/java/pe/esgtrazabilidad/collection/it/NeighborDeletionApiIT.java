package pe.esgtrazabilidad.collection.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;

/** Logical deletion of a neighbour (v3, brief C), against real Postgres. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class NeighborDeletionApiIT {

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

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
    }

    @Test
    void aNeighborWithoutCollectionsIsDeletedWithWhoFromTheTokenAndDisappears() throws Exception {
        String id = createNeighbor("Eliminar IT");
        String scheduleId = createSchedule(id, "MONDAY", "08:00");
        given().when().patch("/neighbors/{n}/schedules/{s}/pause", id, scheduleId).then().statusCode(200);
        createSchedule(id, "FRIDAY", "08:00");

        delete(id, "Registrado por error").then().statusCode(204);

        given().when().get("/neighbors/{id}", id).then().statusCode(404).body("code", equalTo("COL-001"));
        given().queryParam("size", 100).when().get("/neighbors").then()
                .body("content.id", not(hasItem(id)));
        given().contentType(ContentType.JSON)
                .body("{\"fullName\": \"X\", \"address\": \"Jr. 1\", \"districtUbigeo\": \"150112\"}")
                .when().patch("/neighbors/{id}", id)
                .then().statusCode(404).body("code", equalTo("COL-001"));
        changeStatus(id, "deactivate").then().statusCode(404).body("code", equalTo("COL-001"));
        given().contentType(ContentType.JSON)
                .body("{\"dayOfWeek\": \"TUESDAY\", \"time\": \"09:00\"}")
                .when().post("/neighbors/{n}/schedules", id)
                .then().statusCode(404).body("code", equalTo("COL-001"));
        given().when().get("/neighbors/{n}/schedules", id).then()
                .body("content", hasSize(2))
                .body("content.status", everyItem(equalTo("CANCELLED")));

        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT deleted_at, deleted_by_id, deleted_by_email, deletion_reason FROM neighbor WHERE id = ?::uuid")) {
            select.setString(1, id);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getTimestamp("deleted_at")).isNotNull();
                assertThat(row.getString("deleted_by_id")).isEqualTo(TOKEN_SUBJECT);
                assertThat(row.getString("deleted_by_email")).isEqualTo(TestJwtTokens.TEST_EMAIL);
                assertThat(row.getString("deletion_reason")).isEqualTo("Registrado por error");
            }
        }
    }

    @Test
    void aNeighborWithACollectionIsCol011AndStaysAsItWas() {
        String id = createNeighbor("Con Recojo IT");
        createRecord(id).then().statusCode(201);

        delete(id, "Ya no").then().statusCode(409).body("code", equalTo("COL-011"));

        given().when().get("/neighbors/{id}", id).then().statusCode(200).body("status", equalTo("ACTIVE"));
    }

    @Test
    void deletingTwiceAMissingReasonOrAnUnknownIdIsRefused() {
        String id = createNeighbor("Errores Eliminar IT");

        delete(id, " ").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        delete(id, "x".repeat(501)).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        delete(id, "Primera").then().statusCode(204);
        delete(id, "Segunda").then().statusCode(404).body("code", equalTo("COL-001"));
        delete("00000000-0000-0000-0000-000000000000", "x").then().statusCode(404).body("code", equalTo("COL-001"));
    }

    @Test
    void aDeletionWaitsForACollectionBeingRecordedAndThenIsCol011() throws Exception {
        String id = createNeighbor("Carrera Eliminar IT");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection recording = dataSource.getConnection()) {
            // A collection being recorded: the shared lock on the neighbour is
            // held and the record is inserted, but nothing is committed yet.
            recording.setAutoCommit(false);
            try (PreparedStatement lock = recording.prepareStatement(
                    "SELECT id FROM neighbor WHERE id = ?::uuid FOR SHARE")) {
                lock.setString(1, id);
                lock.executeQuery().close();
            }
            try (PreparedStatement insert = recording.prepareStatement(
                    "INSERT INTO collection_record (id, neighbor_id, association_id, collection_date, weight_kg) "
                            + "VALUES (?::uuid, ?::uuid, ?::uuid, DATE '2026-09-07', 4.5)")) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, id);
                insert.setString(3, UUID.randomUUID().toString());
                insert.executeUpdate();
            }

            Future<Response> deletion = pool.submit(() -> delete(id, "Carrera"));

            // The deletion's row lock waits for the recording transaction; if it
            // checked for collections before taking it, it would answer 204 now.
            assertThatThrownBy(() -> deletion.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            recording.commit();

            Response response = deletion.get(30, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.jsonPath().getString("code")).isEqualTo("COL-011");
        } finally {
            pool.shutdownNow();
        }
        given().when().get("/neighbors/{id}", id).then().statusCode(200);
    }

    private Response delete(String id, String reason) {
        return given().contentType(ContentType.JSON)
                .body("{\"reason\": \"%s\"}".formatted(reason))
                .when().delete("/neighbors/{id}", id);
    }

    private Response changeStatus(String id, String action) {
        return given().contentType(ContentType.JSON)
                .body("{\"reason\": \"x\"}")
                .when().patch("/neighbors/{id}/" + action, id);
    }

    private Response createRecord(String neighborId) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"associationId": "%s", "collectionDate": "2026-09-07", "weightKg": 5.5}
                        """.formatted(UUID.randomUUID()))
                .when().post("/neighbors/{n}/collection-records", neighborId);
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
                .when().post("/neighbors/{n}/schedules", neighborId)
                .then().statusCode(201)
                .extract().path("id");
    }
}

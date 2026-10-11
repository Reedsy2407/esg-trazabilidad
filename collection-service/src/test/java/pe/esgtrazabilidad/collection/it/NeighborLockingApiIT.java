package pe.esgtrazabilidad.collection.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
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

/**
 * The row locks that keep schedule writes and edits from interleaving with a
 * deactivation or deletion of the same neighbour, proven against real
 * Postgres: an uncommitted change is held open on a raw JDBC connection while
 * the HTTP request runs on another thread.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class NeighborLockingApiIT {

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
    void aPauseWaitsForAnUncommittedDeletionThenIsCol001AndTheScheduleStaysCancelled() throws Exception {
        String id = createNeighbor("Pausa vs Eliminar IT");
        String scheduleId = createSchedule(id);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection deletion = dataSource.getConnection()) {
            deletion.setAutoCommit(false);
            execute(deletion, "UPDATE neighbor SET deleted_at = now(), deleted_by_id = gen_random_uuid(), "
                    + "deletion_reason = 'IT' WHERE id = ?::uuid", id);
            execute(deletion, "UPDATE collection_schedule SET status = 'CANCELLED' WHERE id = ?::uuid", scheduleId);

            Future<Response> pause = pool.submit(() ->
                    given().when().patch("/neighbors/{n}/schedules/{s}/pause", id, scheduleId));

            assertThatThrownBy(() -> pause.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            deletion.commit();

            Response response = pause.get(30, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(404);
            assertThat(response.jsonPath().getString("code")).isEqualTo("COL-001");
        } finally {
            pool.shutdownNow();
        }
        assertThat(scheduleStatus(scheduleId)).isEqualTo("CANCELLED");
    }

    @Test
    void anEditWaitsForAnUncommittedDeactivationAndThenAnswersTheNewStatus() throws Exception {
        String id = createNeighbor("Editar vs Desactivar IT");
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (Connection deactivation = dataSource.getConnection()) {
            deactivation.setAutoCommit(false);
            execute(deactivation, "UPDATE neighbor SET status = 'INACTIVE' WHERE id = ?::uuid", id);

            Future<Response> edit = pool.submit(() -> edit(id, "Editado durante la desactivación"));

            assertThatThrownBy(() -> edit.get(2, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);
            deactivation.commit();

            Response response = edit.get(30, TimeUnit.SECONDS);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.jsonPath().getString("status")).isEqualTo("INACTIVE");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentEditsOfTheSameNeighborNeverDeadlock() throws Exception {
        String id = createNeighbor("Ediciones simultáneas IT");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < 10; round++) {
                CountDownLatch start = new CountDownLatch(1);
                List<Future<Integer>> edits = new ArrayList<>();
                for (int writer = 0; writer < 2; writer++) {
                    String name = "Edición " + round + "-" + writer;
                    edits.add(pool.submit(() -> {
                        start.await();
                        return edit(id, name).statusCode();
                    }));
                }
                start.countDown();
                for (Future<Integer> edit : edits) {
                    assertThat(edit.get(30, TimeUnit.SECONDS)).isEqualTo(200);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Response edit(String id, String fullName) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"fullName": "%s", "address": "Jr. Editado 1", "districtUbigeo": "150112"}
                        """.formatted(fullName))
                .when().patch("/neighbors/{id}", id);
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

    private String createSchedule(String neighborId) {
        return given().contentType(ContentType.JSON)
                .body("{\"dayOfWeek\": \"MONDAY\", \"time\": \"08:00\"}")
                .when().post("/neighbors/{n}/schedules", neighborId)
                .then().statusCode(201)
                .extract().path("id");
    }

    private static void execute(Connection connection, String sql, String id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private String scheduleStatus(String scheduleId) throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement select = connection.prepareStatement(
                        "SELECT status FROM collection_schedule WHERE id = ?::uuid")) {
            select.setString(1, scheduleId);
            try (ResultSet row = select.executeQuery()) {
                assertThat(row.next()).isTrue();
                return row.getString("status");
            }
        }
    }
}

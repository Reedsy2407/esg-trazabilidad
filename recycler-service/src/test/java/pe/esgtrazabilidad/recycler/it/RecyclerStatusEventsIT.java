package pe.esgtrazabilidad.recycler.it;

import java.util.List;
import java.util.UUID;

import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.recycler.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.recycler.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * CA-22 (recycler side) and CA-23 (emitting side): RecyclerStatusChanged on
 * create / activate / deactivate, and the ADMIN-only republish with one event
 * per existing recycler. Reads the outbox table: whether the dispatcher has
 * already moved an entry on is irrelevant, so the checks never depend on status.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class RecyclerStatusEventsIT {

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
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
    }

    @Test
    void createActivateAndDeactivateEmitTheCurrentStatus() {
        String associationId = createAssociation("20444444445");
        String recyclerId = createRecycler(associationId, "40000001");

        List<String> afterCreate = eventsFor(recyclerId);
        assertThat(afterCreate).hasSize(1);
        assertThat(afterCreate.get(0))
                .contains("\"status\":\"ACTIVE\"")
                .contains("\"associationId\":\"" + associationId + "\"");

        given().patch("/associations/{a}/recyclers/{r}/deactivate", associationId, recyclerId)
                .then()
                .statusCode(200);
        given().patch("/associations/{a}/recyclers/{r}/activate", associationId, recyclerId)
                .then()
                .statusCode(200);

        List<String> all = eventsFor(recyclerId);
        assertThat(all).hasSize(3);
        assertThat(all).filteredOn(p -> p.contains("\"status\":\"INACTIVE\"")).hasSize(1);
        assertThat(all).filteredOn(p -> p.contains("\"status\":\"ACTIVE\"")).hasSize(2);
        assertThat(jdbc.queryForList(
                        "select routing_key from outbox_event_recycler where payload_json like ?", String.class,
                        "%" + recyclerId + "%"))
                .containsOnly("recycler.status.changed");
    }

    @Test
    void aFailedTransitionLeavesNoEventBehind() {
        String associationId = createAssociation("20555555556");
        String recyclerId = createRecycler(associationId, "40000002");
        given().patch("/associations/{a}/recyclers/{r}/activate", associationId, recyclerId)
                .then()
                .statusCode(409);

        assertThat(eventsFor(recyclerId)).hasSize(1);
    }

    @Test
    void republishEmitsOneEventPerExistingRecyclerWithItsCurrentStatus() {
        String associationId = createAssociation("20666666667");
        String active = createRecycler(associationId, "40000003");
        String inactive = createRecycler(associationId, "40000004");
        given().patch("/associations/{a}/recyclers/{r}/deactivate", associationId, inactive)
                .then()
                .statusCode(200);
        int before = eventsFor(active).size();
        int beforeInactive = eventsFor(inactive).size();

        asToken(TestJwtTokens.adminToken())
                .post("/recyclers/status-events/republish")
                .then()
                .statusCode(200)
                .body("published", greaterThanOrEqualTo(2));

        List<String> activeEvents = eventsFor(active);
        List<String> inactiveEvents = eventsFor(inactive);
        assertThat(activeEvents).hasSize(before + 1);
        assertThat(inactiveEvents).hasSize(beforeInactive + 1);
        // Invariant, not arrival order: the new event carries each recycler's current status.
        assertThat(activeEvents).allMatch(payload -> payload.contains("\"status\":\"ACTIVE\""));
        assertThat(inactiveEvents).filteredOn(p -> p.contains("\"status\":\"INACTIVE\"")).hasSize(2);
        assertThat(inactiveEvents).filteredOn(p -> p.contains("\"status\":\"ACTIVE\"")).hasSize(1);

        // Repeatable without harm: another round writes another set of distinct events.
        asToken(TestJwtTokens.adminToken())
                .post("/recyclers/status-events/republish")
                .then()
                .statusCode(200);
        assertThat(eventsFor(active)).hasSize(before + 2);
        assertThat(jdbc.queryForObject("select count(distinct id) = count(*) from outbox_event_recycler", Boolean.class))
                .isTrue();
    }

    @Test
    void staffCannotRepublishAndGetsAuth000() {
        int before = outboxCount();

        given().post("/recyclers/status-events/republish")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));

        assertThat(outboxCount()).isEqualTo(before);
    }

    @Test
    void aRecyclerTokenCannotRepublishEither() {
        asToken(TestJwtTokens.recyclerToken())
                .post("/recyclers/status-events/republish")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
    }

    /** Requests as another actor: drops the default STAFF header, which would be read first. */
    private io.restassured.specification.RequestSpecification asToken(String token) {
        RestAssuredSetup.anonymous(port);
        return given().header("Authorization", "Bearer " + token);
    }

    private int outboxCount() {
        return jdbc.queryForObject("select count(*) from outbox_event_recycler", Integer.class);
    }

    private List<String> eventsFor(String recyclerId) {
        return jdbc.queryForList(
                "select payload_json from outbox_event_recycler where routing_key = 'recycler.status.changed'"
                        + " and payload_json like ? order by created_at",
                String.class,
                "%\"recyclerId\":\"" + recyclerId + "\"%");
    }

    private String createAssociation(String ruc) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"name":"Asociación eventos IT","ruc":"%s","registrationNumber":"REG-EV-1",
                         "address":"Dirección de prueba","contactEmail":"it@test.pe","contactPhone":"999999999"}
                        """.formatted(ruc))
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createRecycler(String associationId, String dniSuffix) {
        String dni = (dniSuffix + UUID.randomUUID().toString().replaceAll("\\D", "") + "00000000").substring(0, 8);
        return given().contentType(ContentType.JSON)
                .body("""
                        {"fullName":"Reciclador evento","dni":"%s","phone":"999999999"}
                        """.formatted(dni))
                .post("/associations/{a}/recyclers", associationId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }
}

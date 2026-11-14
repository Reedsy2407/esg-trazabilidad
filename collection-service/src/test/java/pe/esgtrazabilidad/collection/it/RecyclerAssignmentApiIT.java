package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.UUID;

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

import io.restassured.http.ContentType;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/** CA-30 and CA-31: explicit replacement (with moves) and the district-wide assignment. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class RecyclerAssignmentApiIT {

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
    private RecyclerStatusCopyRepository copies;

    @Autowired
    private JdbcTemplate jdbc;

    private final String association = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        RestAssuredSetup.authenticated(port);
    }

    private String activeRecycler() {
        String id = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, id, association, RecyclerStatus.ACTIVE);
        return id;
    }

    private String put(String recyclerId, String... scheduleIds) {
        String body = "{\"scheduleIds\": [" + String.join(",", java.util.Arrays.stream(scheduleIds).map(s -> "\"" + s + "\"").toList()) + "]}";
        return given().contentType(ContentType.JSON).body(body).put("/recyclers/{id}/assignment", recyclerId).then().extract().asString();
    }

    private long assignedEvents() {
        Long count = jdbc.queryForObject(
                "select count(*) from outbox_event_collection where routing_key = 'collection.recycler.assigned'", Long.class);
        return count == null ? 0 : count;
    }

    @Test
    void putLeavesExactlyTheSentListAndStoresTheRecyclersAssociation() {
        String recycler = activeRecycler();
        String s1 = FieldFixtures.schedule(FieldFixtures.neighbor("Ana PUT", "150101"), "MONDAY", "08:00:00");
        String s2 = FieldFixtures.schedule(FieldFixtures.neighbor("Beto PUT", "150101"), "TUESDAY", "08:00:00");
        String s3 = FieldFixtures.schedule(FieldFixtures.neighbor("Carla PUT", "150101"), "WEDNESDAY", "08:00:00");

        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [\"%s\", \"%s\"]}".formatted(s1, s2))
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(200)
                .body("scheduleIds", containsInAnyOrder(s1, s2))
                .body("moved", empty());
        // A second PUT with a different list replaces, it does not add.
        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [\"%s\", \"%s\"]}".formatted(s2, s3))
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(200);

        given().get("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(200)
                .body("schedules.scheduleId", containsInAnyOrder(s2, s3))
                .body("schedules", hasSize(2));
        assertThat(jdbc.queryForList("select association_id::text from recycler_assignment where recycler_id = ?::uuid", String.class, recycler))
                .containsOnly(association);
    }

    @Test
    void putMovesTheSchedulesAnotherRecyclerHadAndListsThem() {
        String first = activeRecycler();
        String second = activeRecycler();
        String s1 = FieldFixtures.schedule(FieldFixtures.neighbor("Dora MOVE", "150103"), "MONDAY", "09:00:00");
        String s2 = FieldFixtures.schedule(FieldFixtures.neighbor("Eli MOVE", "150103"), "TUESDAY", "09:00:00");
        put(first, s1, s2);

        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [\"%s\"]}".formatted(s1))
                .put("/recyclers/{id}/assignment", second)
                .then()
                .statusCode(200)
                .body("moved", hasSize(1))
                .body("moved[0].scheduleId", equalTo(s1))
                .body("moved[0].fromRecyclerId", equalTo(first));

        given().get("/recyclers/{id}/assignment", first).then().body("schedules.scheduleId", containsInAnyOrder(s2));
        given().get("/recyclers/{id}/assignment", second).then().body("schedules.scheduleId", containsInAnyOrder(s1));
    }

    @Test
    void anUnknownOrCancelledScheduleIsCol006AndNothingChanges() {
        String recycler = activeRecycler();
        String neighbor = FieldFixtures.neighbor("Fede CANCEL", "150104");
        String good = FieldFixtures.schedule(neighbor, "MONDAY", "10:00:00");
        String cancelled = FieldFixtures.schedule(neighbor, "TUESDAY", "10:00:00");
        given().patch("/neighbors/{n}/schedules/{s}/cancel", neighbor, cancelled).then().statusCode(200);
        put(recycler, good);

        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [\"%s\", \"%s\"]}".formatted(good, cancelled))
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-006"));
        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [\"%s\"]}".formatted(UUID.randomUUID()))
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-006"));

        given().get("/recyclers/{id}/assignment", recycler).then().body("schedules.scheduleId", containsInAnyOrder(good));
    }

    @Test
    void aRecyclerWithoutCopyOrInactiveIsCol019() {
        String schedule = FieldFixtures.schedule(FieldFixtures.neighbor("Gaby COPY", "150105"), "MONDAY", "11:00:00");
        String noCopy = UUID.randomUUID().toString();
        String inactive = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, inactive, association, RecyclerStatus.INACTIVE);

        for (String recycler : List.of(noCopy, inactive)) {
            given().contentType(ContentType.JSON)
                    .body("{\"scheduleIds\": [\"%s\"]}".formatted(schedule))
                    .put("/recyclers/{id}/assignment", recycler)
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("COL-019"));
            given().contentType(ContentType.JSON)
                    .body("{\"recyclerId\": \"%s\", \"districtUbigeo\": \"150105\"}".formatted(recycler))
                    .post("/assignments/bulk")
                    .then()
                    .statusCode(403)
                    .body("code", equalTo("COL-019"));
        }
    }

    @Test
    void aRecyclerTokenCannotAssign() {
        String recycler = activeRecycler();
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken())
                .contentType(ContentType.JSON)
                .body("{\"scheduleIds\": []}")
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken())
                .contentType(ContentType.JSON)
                .body("{\"recyclerId\": \"%s\", \"districtUbigeo\": \"150101\"}".formatted(recycler))
                .post("/assignments/bulk")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken())
                .get("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
    }

    @Test
    void bulkAssignsOnlyTheFreeSchedulesOfActiveNeighboursAndIsIdempotent() {
        String recycler = activeRecycler();
        String other = activeRecycler();
        String district = "070101";
        String free1 = FieldFixtures.schedule(FieldFixtures.neighbor("Hugo BULK", district), "MONDAY", "07:00:00");
        String free2 = FieldFixtures.schedule(FieldFixtures.neighbor("Iris BULK", district), "MONDAY", "07:30:00");
        String taken = FieldFixtures.schedule(FieldFixtures.neighbor("Juan BULK", district), "MONDAY", "08:00:00");
        put(other, taken);
        String inactiveNeighbor = FieldFixtures.neighbor("Kira BULK", district);
        FieldFixtures.schedule(inactiveNeighbor, "TUESDAY", "08:00:00");
        FieldFixtures.deactivateNeighbor(inactiveNeighbor);
        String pausedNeighbor = FieldFixtures.neighbor("Mara BULK PAUSADA", district);
        String paused = FieldFixtures.schedule(pausedNeighbor, "WEDNESDAY", "08:00:00");
        given().patch("/neighbors/{n}/schedules/{s}/pause", pausedNeighbor, paused).then().statusCode(200);
        FieldFixtures.schedule(FieldFixtures.neighbor("Luz OTRO DISTRITO", "070102"), "MONDAY", "07:00:00");
        long eventsBefore = assignedEvents();

        String body = "{\"recyclerId\": \"%s\", \"districtUbigeo\": \"%s\"}".formatted(recycler, district);
        given().contentType(ContentType.JSON)
                .body(body)
                .post("/assignments/bulk")
                .then()
                .statusCode(200)
                .body("assigned", equalTo(2))
                .body("skippedOtherRecycler", equalTo(1))
                .body("skippedInactive", equalTo(2)); // the inactive neighbour's schedule and the paused one

        given().get("/recyclers/{id}/assignment", recycler).then().body("schedules.scheduleId", containsInAnyOrder(free1, free2));
        given().get("/recyclers/{id}/assignment", other).then().body("schedules.scheduleId", containsInAnyOrder(taken));
        assertThat(assignedEvents()).as("one RecyclerAssigned for the whole district").isEqualTo(eventsBefore + 1);

        // Repeating the request changes nothing and emits nothing.
        given().contentType(ContentType.JSON)
                .body(body)
                .post("/assignments/bulk")
                .then()
                .statusCode(200)
                .body("assigned", equalTo(0))
                .body("skippedOtherRecycler", equalTo(1));
        given().get("/recyclers/{id}/assignment", recycler).then().body("schedules", hasSize(2));
        assertThat(assignedEvents()).isEqualTo(eventsBefore + 1);
    }

    @Test
    void bulkRejectsADistrictOutsideLimaAndCallao() {
        String recycler = activeRecycler();
        given().contentType(ContentType.JSON)
                .body("{\"recyclerId\": \"%s\", \"districtUbigeo\": \"999999\"}".formatted(recycler))
                .post("/assignments/bulk")
                .then()
                .statusCode(400)
                .body("code", equalTo("COL-010"));
    }

    @Test
    void putEmitsOneEventPerChangedScheduleAndNoneWhenRepeated() {
        String recycler = activeRecycler();
        String s1 = FieldFixtures.schedule(FieldFixtures.neighbor("Mia EVT", "150101"), "FRIDAY", "12:00:00");
        String s2 = FieldFixtures.schedule(FieldFixtures.neighbor("Nico EVT", "150101"), "FRIDAY", "12:30:00");
        long before = assignedEvents();

        put(recycler, s1, s2);
        assertThat(assignedEvents()).isEqualTo(before + 2);
        put(recycler, s2, s1);
        assertThat(assignedEvents()).as("same set: nothing changed").isEqualTo(before + 2);

        String payload = jdbc.queryForObject(
                "select payload_json from outbox_event_collection where routing_key = 'collection.recycler.assigned' order by created_at desc limit 1",
                String.class);
        assertThat(payload).contains("\"recyclerId\":\"" + recycler + "\"").contains("\"districtUbigeo\":\"150101\"");
    }
}

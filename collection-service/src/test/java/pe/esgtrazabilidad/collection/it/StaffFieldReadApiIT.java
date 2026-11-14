package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.specification.RequestSpecification;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.InMemoryEvidenceStorage;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/** CA-38 (A.5c): what the staff reads of a recycler's field work, and the schedules the console can assign. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
@Import(InMemoryEvidenceStorage.Config.class)
class StaffFieldReadApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String SHA256 = "b".repeat(64);
    private static final Instant DAY_ONE = Instant.parse("2026-11-10T13:00:00Z");
    private static final Instant DAY_TWO = Instant.parse("2026-11-12T13:00:00Z");
    private static final Instant DAY_THREE = Instant.parse("2026-11-14T13:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private RecyclerStatusCopyRepository copies;

    @Autowired
    private InMemoryEvidenceStorage bucket;

    @Autowired
    private JdbcTemplate jdbc;

    private String association;
    private String recycler;
    private String neighbor;
    private String neighborName;
    private String schedule;

    @BeforeEach
    void setUp() {
        bucket.reset();
        association = UUID.randomUUID().toString();
        recycler = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, recycler, association, RecyclerStatus.ACTIVE);
        RestAssuredSetup.authenticated(port);
        neighborName = "Vecino LECTURA " + UUID.randomUUID().toString().substring(0, 8);
        neighbor = FieldFixtures.neighbor(neighborName, "150101");
        schedule = FieldFixtures.schedule(neighbor, "SATURDAY", "09:00:00");
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\"]}".formatted(schedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);
        RestAssuredSetup.anonymous(port);
    }

    private RequestSpecification device() {
        return given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recycler, association))
                .contentType(ContentType.JSON);
    }

    private RequestSpecification staff() {
        return given().header("Authorization", "Bearer " + TestJwtTokens.validToken());
    }

    private RequestSpecification admin() {
        return given().header("Authorization", "Bearer " + TestJwtTokens.adminToken());
    }

    private String openWorkday(Instant startedAt) {
        return device().body("{\"startedAt\": \"%s\", \"clientId\": \"%s\"}".formatted(startedAt, UUID.randomUUID()))
                .post("/workdays").then().statusCode(201).extract().path("workdayId");
    }

    private void sendPoint(String workday, long deviceSeq, Instant recordedAt, double lat, double lng) {
        device().body("{\"points\": [{\"lat\": %s, \"lng\": %s, \"accuracyM\": 12.5, \"recordedAt\": \"%s\", \"deviceSeq\": %d}]}"
                        .formatted(lat, lng, recordedAt, deviceSeq))
                .post("/workdays/{id}/points", workday).then().statusCode(200);
    }

    private Map<String, Object> checkin(String workday, long deviceSeq, Instant recordedAt, String weight, String extra) {
        return device().body("""
                        {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": %s,
                         "wasteType": "PET", "deviceSeq": %d %s}
                        """.formatted(UUID.randomUUID(), neighbor, schedule, recordedAt, weight, deviceSeq, extra))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("$");
    }

    /** A check-in whose client id is the one the photo was agreed for. */
    private Map<String, Object> checkinWithPhoto(String workday, long deviceSeq, Instant recordedAt, String weight) {
        UUID clientId = UUID.randomUUID();
        String key = device().body("{\"checkinClientId\": \"%s\", \"contentType\": \"image/jpeg\", \"sizeBytes\": 4000, \"sha256\": \"%s\"}"
                        .formatted(clientId, SHA256))
                .post("/evidence/presign").then().statusCode(200).extract().path("key");
        bucket.put(key, 4000);
        return device().body("""
                        {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": %s,
                         "wasteType": "PET", "deviceSeq": %d, "photoKey": "%s", "lat": -12.0464, "lng": -77.0428, "accuracyM": 8.0}
                        """.formatted(clientId, neighbor, schedule, recordedAt, weight, deviceSeq, key))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("$");
    }

    // ---- GET /recyclers/{id}/workdays -------------------------------------------------------------------------------

    @Test
    void listsTheRecyclersWorkdaysMostRecentFirstWithTheirCounts() {
        String first = openWorkday(DAY_ONE);
        String second = openWorkday(DAY_TWO);
        String third = openWorkday(DAY_THREE);
        sendPoint(second, 1, DAY_TWO.plusSeconds(10), -12.04, -77.03);
        sendPoint(second, 2, DAY_TWO.plusSeconds(20), -12.05, -77.04);
        checkin(second, 3, DAY_TWO.plusSeconds(30), "2.50", "");
        checkin(second, 4, DAY_TWO.plusSeconds(40), "1.25", "");
        device().body("{\"endedAt\": \"%s\"}".formatted(DAY_TWO.plusSeconds(3600))).post("/workdays/{id}/close", second)
                .then().statusCode(200);

        JsonPath body = staff().get("/recyclers/{id}/workdays", recycler).then().statusCode(200).extract().jsonPath();

        assertThat(body.getList("content.workdayId")).containsExactly(third, second, first);
        assertThat(body.getInt("totalElements")).isEqualTo(3);
        assertThat(body.getInt("page")).isZero();
        assertThat(body.getString("content[1].status")).isEqualTo("CLOSED");
        assertThat(body.getString("content[0].status")).isEqualTo("OPEN");
        assertThat(body.getString("content[0].endedAt")).isNull();
        assertThat(body.getInt("content[1].checkinCount")).isEqualTo(2);
        assertThat(body.getFloat("content[1].totalKg")).isEqualTo(3.75f);
        assertThat(body.getInt("content[1].pointCount")).isEqualTo(2);
        assertThat(body.getBoolean("content[1].withGps")).isTrue();
        assertThat(body.getInt("content[0].checkinCount")).isZero();
        assertThat(body.getFloat("content[0].totalKg")).isZero();
        assertThat(Instant.parse(body.getString("content[1].startedAt"))).isEqualTo(DAY_TWO);
    }

    @Test
    void filtersByStartedAtAndPaginates() {
        String first = openWorkday(DAY_ONE);
        String second = openWorkday(DAY_TWO);
        String third = openWorkday(DAY_THREE);

        assertThat(staff().queryParam("from", DAY_TWO.toString()).get("/recyclers/{id}/workdays", recycler)
                        .then().statusCode(200).extract().jsonPath().getList("content.workdayId"))
                .containsExactly(third, second);
        assertThat(staff().queryParam("to", DAY_TWO.toString()).get("/recyclers/{id}/workdays", recycler)
                        .then().statusCode(200).extract().jsonPath().getList("content.workdayId"))
                .containsExactly(second, first);
        assertThat(staff().queryParam("from", DAY_ONE.plusSeconds(1).toString()).queryParam("to", DAY_THREE.minusSeconds(1).toString())
                        .get("/recyclers/{id}/workdays", recycler).then().statusCode(200).extract().jsonPath().getList("content.workdayId"))
                .containsExactly(second);

        JsonPath secondPage = staff().queryParam("size", 2).queryParam("page", 1).get("/recyclers/{id}/workdays", recycler)
                .then().statusCode(200).extract().jsonPath();
        assertThat(secondPage.getList("content.workdayId")).containsExactly(first);
        assertThat(secondPage.getInt("totalPages")).isEqualTo(2);
        assertThat(secondPage.getInt("totalElements")).isEqualTo(3);
    }

    @Test
    void aRecyclerWithoutWorkdaysOrAnUnknownIdAnswersAnEmptyPage() {
        staff().get("/recyclers/{id}/workdays", recycler).then().statusCode(200)
                .body("content.size()", equalTo(0)).body("totalElements", equalTo(0));
        staff().get("/recyclers/{id}/workdays", UUID.randomUUID()).then().statusCode(200)
                .body("content.size()", equalTo(0)).body("totalElements", equalTo(0));
    }

    // ---- GET /workdays/{id} -----------------------------------------------------------------------------------------

    @Test
    void theDetailOrdersPointsByDeviceSeqAndShowsCheckinsWithTheirPhotoState() {
        String workday = openWorkday(DAY_THREE);
        // Arrival order is not the order of the route: a phone clock that jumped back must not scramble it.
        sendPoint(workday, 3, DAY_THREE.plusSeconds(100), -12.03, -77.03);
        sendPoint(workday, 1, DAY_THREE.plusSeconds(300), -12.01, -77.01);
        sendPoint(workday, 2, DAY_THREE.plusSeconds(200), -12.02, -77.02);
        Map<String, Object> withPhoto = checkinWithPhoto(workday, 4, DAY_THREE.plusSeconds(400), "2.50");
        Map<String, Object> noPhoto = checkin(workday, 5, DAY_THREE.plusSeconds(500), "1.00", "");
        Map<String, Object> deleted = checkinWithPhoto(workday, 6, DAY_THREE.plusSeconds(600), "3.00");
        jdbc.update("update field_checkin set photo_deleted_at = now() where id = ?::uuid", deleted.get("checkinId"));

        JsonPath body = staff().get("/workdays/{id}", workday).then().statusCode(200).extract().jsonPath();

        assertThat(body.getString("workdayId")).isEqualTo(workday);
        assertThat(body.getString("recyclerId")).isEqualTo(recycler);
        assertThat(body.getString("associationId")).isEqualTo(association);
        assertThat(body.getBoolean("withGps")).isTrue();
        assertThat(body.getList("points.deviceSeq", Integer.class)).containsExactly(1, 2, 3);
        assertThat(body.getInt("pointsTotal")).isEqualTo(3);
        assertThat(body.getInt("pointsOffset")).isZero();
        assertThat(body.getFloat("points[0].lat")).isEqualTo(-12.01f);
        assertThat(body.getFloat("points[0].accuracyM")).isEqualTo(12.5f);
        assertThat(body.getString("routeSummary")).isNull();

        assertThat(body.getList("checkins.checkinId")).containsExactly(withPhoto.get("checkinId"), noPhoto.get("checkinId"), deleted.get("checkinId"));
        assertThat(body.getString("checkins[0].recordId")).isEqualTo((String) withPhoto.get("recordId"));
        assertThat(body.getString("checkins[0].neighborName")).isEqualTo(neighborName);
        assertThat(body.getString("checkins[0].neighborId")).isEqualTo(neighbor);
        assertThat(body.getString("checkins[0].scheduleId")).isEqualTo(schedule);
        assertThat(body.getFloat("checkins[0].weightKg")).isEqualTo(2.5f);
        assertThat(body.getString("checkins[0].wasteType")).isEqualTo("PET");
        assertThat(body.getFloat("checkins[0].lat")).isEqualTo(-12.0464f);
        assertThat(body.getFloat("checkins[0].accuracyM")).isEqualTo(8.0f);
        assertThat(body.getString("checkins[0].photo.state")).isEqualTo("AVAILABLE");
        assertThat(body.getString("checkins[0].photo.url")).startsWith("https://bucket.invalid/evidence/").contains("signed-read");
        assertThat(bucket.lastReadValidity()).isEqualTo(Duration.ofMinutes(5));

        assertThat(body.getString("checkins[1].photo.state")).isEqualTo("NONE");
        assertThat(body.getString("checkins[1].photo.url")).isNull();
        assertThat(body.getString("checkins[1].lat")).isNull();

        assertThat(body.getString("checkins[2].photo.state")).isEqualTo("DELETED");
        assertThat(body.getString("checkins[2].photo.url")).isNull();
    }

    @Test
    void theDetailNeverCarriesTheNeighboursPhoneAndSigningHappensOutsideATransaction() {
        String workday = openWorkday(DAY_THREE);
        checkinWithPhoto(workday, 1, DAY_THREE.plusSeconds(10), "2.00");
        // The check-in's own bucket check (head) is already recorded; the read below must add one signing, outside any transaction.
        int before = bucket.callsInsideTransaction().size();

        staff().get("/workdays/{id}", workday).then().statusCode(200).body(not(containsString("phone"))).body(not(containsString("999000111")));

        assertThat(bucket.callsInsideTransaction()).hasSize(before + 1).containsOnly(false);
    }

    @Test
    void pointsWithTheSameDeviceSeqAreOrderedByTheirClock() {
        String workday = openWorkday(DAY_THREE);
        sendPoint(workday, 1, DAY_THREE.plusSeconds(50), -12.02, -77.02);
        sendPoint(workday, 1, DAY_THREE.plusSeconds(20), -12.01, -77.01);

        JsonPath body = staff().get("/workdays/{id}", workday).then().statusCode(200).extract().jsonPath();

        assertThat(body.getList("points.lat", Float.class)).containsExactly(-12.01f, -12.02f);
    }

    @Test
    void afterTheRetentionTheDetailHasNoPointsAndShowsTheRouteSummary() {
        String workday = openWorkday(DAY_ONE);
        sendPoint(workday, 1, DAY_ONE.plusSeconds(10), -12.01, -77.01);
        sendPoint(workday, 2, DAY_ONE.plusSeconds(20), -12.02, -77.02);
        // What the retention does in one transaction: summarise and delete the exact points.
        jdbc.update(
                "insert into route_summary (workday_id, point_count, distance_m, first_at, last_at, polyline, summarized_at) "
                        + "values (?::uuid, 2, 1573.4, ?, ?, '[[-12.01,-77.01],[-12.02,-77.02]]', now())",
                workday, Timestamp.from(DAY_ONE.plusSeconds(10)), Timestamp.from(DAY_ONE.plusSeconds(20)));
        jdbc.update("delete from workday_point where workday_id = ?::uuid", workday);

        JsonPath body = staff().get("/workdays/{id}", workday).then().statusCode(200).extract().jsonPath();

        assertThat(body.getList("points")).isEmpty();
        assertThat(body.getInt("pointsTotal")).isZero();
        assertThat(body.getInt("routeSummary.pointCount")).isEqualTo(2);
        assertThat(body.getFloat("routeSummary.distanceM")).isEqualTo(1573.4f);
        assertThat(Instant.parse(body.getString("routeSummary.firstAt"))).isEqualTo(DAY_ONE.plusSeconds(10));
        assertThat(Instant.parse(body.getString("routeSummary.lastAt"))).isEqualTo(DAY_ONE.plusSeconds(20));
        assertThat(body.getList("routeSummary.polyline")).hasSize(2);
        assertThat(body.getFloat("routeSummary.polyline[1][0]")).isEqualTo(-12.02f);

        // The list counts what the summary remembers.
        assertThat(staff().get("/recyclers/{id}/workdays", recycler).then().statusCode(200).extract().jsonPath()
                        .getInt("content[0].pointCount"))
                .isEqualTo(2);
    }

    @Test
    void thePointsAreServedInWindowsWhenTheCallerAsksForThem() {
        String workday = openWorkday(DAY_THREE);
        for (int i = 1; i <= 5; i++) {
            sendPoint(workday, i, DAY_THREE.plusSeconds(i * 10L), -12.0 - i / 100.0, -77.0);
        }

        JsonPath window = staff().queryParam("pointsOffset", 2).queryParam("pointsLimit", 2).get("/workdays/{id}", workday)
                .then().statusCode(200).extract().jsonPath();

        assertThat(window.getList("points.deviceSeq", Integer.class)).containsExactly(3, 4);
        assertThat(window.getInt("pointsTotal")).isEqualTo(5);
        assertThat(window.getInt("pointsOffset")).isEqualTo(2);
    }

    @Test
    void readingDoesNotWriteAnything() {
        String workday = openWorkday(DAY_THREE);
        checkinWithPhoto(workday, 1, DAY_THREE.plusSeconds(10), "2.00");
        Map<String, Object> before = counts();

        staff().get("/workdays/{id}", workday).then().statusCode(200);
        staff().get("/recyclers/{id}/workdays", recycler).then().statusCode(200);
        staff().queryParam("districtUbigeo", "150101").get("/schedules/assignable").then().statusCode(200);

        assertThat(counts()).isEqualTo(before);
    }

    private Map<String, Object> counts() {
        return jdbc.queryForMap(
                "select (select count(*) from workday) w, (select count(*) from workday_point) p, (select count(*) from field_checkin) c, "
                        + "(select count(*) from collection_record) r, (select count(*) from recycler_assignment) a, "
                        + "(select count(*) from outbox_event_collection) o");
    }

    @Test
    void anUnknownWorkdayIsNotFound() {
        staff().get("/workdays/{id}", UUID.randomUUID()).then().statusCode(404).body("code", equalTo("COL-016"));
    }

    // ---- roles ------------------------------------------------------------------------------------------------------

    @Test
    void aRecyclerTokenGets403OnEveryStaffReadAndAnAdminTokenCanRead() {
        String workday = openWorkday(DAY_THREE);

        device().get("/workdays/{id}", workday).then().statusCode(403).body("code", equalTo("AUTH-000"));
        device().get("/recyclers/{id}/workdays", recycler).then().statusCode(403).body("code", equalTo("AUTH-000"));
        device().queryParam("districtUbigeo", "150101").get("/schedules/assignable").then().statusCode(403).body("code", equalTo("AUTH-000"));

        admin().get("/workdays/{id}", workday).then().statusCode(200);
        admin().get("/recyclers/{id}/workdays", recycler).then().statusCode(200);
    }

    @Test
    void withoutATokenTheStaffReadsAnswer401WithAJsonBody() {
        given().get("/workdays/{id}", UUID.randomUUID()).then().statusCode(401).body("code", equalTo("AUTH-000"));
        given().get("/recyclers/{id}/workdays", recycler).then().statusCode(401).body("code", equalTo("AUTH-000"));
        given().queryParam("districtUbigeo", "150101").get("/schedules/assignable").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void theDevicesOwnWorkdayRoutesStillWork() {
        // The staff matcher only takes GET /workdays/{id}: opening, points and closing stay the recycler's.
        String workday = openWorkday(DAY_THREE);
        staff().contentType(ContentType.JSON).body("{\"startedAt\": \"%s\", \"clientId\": \"%s\"}".formatted(DAY_THREE, UUID.randomUUID()))
                .post("/workdays").then().statusCode(403).body("code", equalTo("AUTH-000"));
        device().body("{\"endedAt\": \"%s\"}".formatted(DAY_THREE.plusSeconds(60))).post("/workdays/{id}/close", workday).then().statusCode(200);
    }

    // ---- GET /schedules/assignable ----------------------------------------------------------------------------------

    @Test
    void assignableSchedulesShowFreeOnesAndThoseOfAnotherRecyclerButNotInactiveNeighboursNorOtherDistricts() {
        String district = "150103";
        RestAssuredSetup.authenticated(port);
        String freeNeighbor = FieldFixtures.neighbor("Libre ASIGNABLE " + UUID.randomUUID(), district);
        String freeSchedule = FieldFixtures.schedule(freeNeighbor, "MONDAY", "08:00:00");
        String takenNeighbor = FieldFixtures.neighbor("Ocupado ASIGNABLE " + UUID.randomUUID(), district);
        String takenSchedule = FieldFixtures.schedule(takenNeighbor, "TUESDAY", "10:30:00");
        String inactiveNeighbor = FieldFixtures.neighbor("Inactivo ASIGNABLE " + UUID.randomUUID(), district);
        String inactiveSchedule = FieldFixtures.schedule(inactiveNeighbor, "WEDNESDAY", "09:00:00");
        String elsewhereNeighbor = FieldFixtures.neighbor("Otro distrito ASIGNABLE " + UUID.randomUUID(), "150102");
        String elsewhereSchedule = FieldFixtures.schedule(elsewhereNeighbor, "THURSDAY", "09:00:00");
        String cancelledSchedule = FieldFixtures.schedule(freeNeighbor, "FRIDAY", "09:00:00");
        given().patch("/neighbors/{n}/schedules/{s}/cancel", freeNeighbor, cancelledSchedule).then().statusCode(200);
        FieldFixtures.deactivateNeighbor(inactiveNeighbor);
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\"]}".formatted(takenSchedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);

        JsonPath body = given().queryParam("districtUbigeo", district).get("/schedules/assignable").then().statusCode(200).extract().jsonPath();

        List<String> ids = body.getList("content.scheduleId");
        assertThat(ids).containsExactlyInAnyOrder(freeSchedule, takenSchedule).doesNotContain(inactiveSchedule, elsewhereSchedule, cancelledSchedule);
        assertThat(body.getInt("totalElements")).isEqualTo(2);
        int free = ids.indexOf(freeSchedule);
        int taken = ids.indexOf(takenSchedule);
        assertThat(body.getString("content[" + free + "].assignedRecyclerId")).isNull();
        assertThat(body.getString("content[" + taken + "].assignedRecyclerId")).isEqualTo(recycler);
        assertThat(body.getString("content[" + free + "].dayOfWeek")).isEqualTo("MONDAY");
        assertThat(body.getString("content[" + free + "].time")).startsWith("08:00");
        assertThat(body.getString("content[" + free + "].neighbor.id")).isEqualTo(freeNeighbor);
        assertThat(body.getString("content[" + free + "].neighbor.fullName")).startsWith("Libre ASIGNABLE");
        assertThat(body.getString("content[" + free + "].neighbor.address")).isEqualTo("Calle Falsa 123");
        assertThat(body.getString("content[" + free + "].neighbor.district")).isNotBlank();
        given().queryParam("districtUbigeo", district).get("/schedules/assignable").then().body(not(containsString("phone")));
    }

    @Test
    void assignableSchedulesPaginate() {
        String district = "150104";
        RestAssuredSetup.authenticated(port);
        for (int i = 0; i < 3; i++) {
            String n = FieldFixtures.neighbor("Pagina %d ASIGNABLE ".formatted(i) + UUID.randomUUID(), district);
            FieldFixtures.schedule(n, "MONDAY", "08:00:00");
        }

        JsonPath page = given().queryParam("districtUbigeo", district).queryParam("size", 2).queryParam("page", 1)
                .get("/schedules/assignable").then().statusCode(200).extract().jsonPath();

        assertThat(page.getList("content")).hasSize(1);
        assertThat(page.getInt("totalElements")).isEqualTo(3);
        assertThat(page.getInt("totalPages")).isEqualTo(2);
    }

    @Test
    void assignableSchedulesNeedAValidDistrict() {
        RestAssuredSetup.authenticated(port);
        given().get("/schedules/assignable").then().statusCode(400).body("code", equalTo("COL-010"));
        given().queryParam("districtUbigeo", "15").get("/schedules/assignable").then().statusCode(400).body("code", equalTo("COL-010"));
        given().queryParam("districtUbigeo", "999999").get("/schedules/assignable").then().statusCode(400).body("code", equalTo("COL-010"));
    }
}

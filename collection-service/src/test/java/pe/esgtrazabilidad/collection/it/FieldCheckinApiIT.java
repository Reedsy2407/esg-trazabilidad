package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import io.restassured.specification.RequestSpecification;
import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;
import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;
import pe.esgtrazabilidad.collection.fieldwork.FieldCheckinService;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/**
 * CA-35, CA-36, CA-47 and the server side of CA-26. The JVM's default zone is set to one that is
 * neither Lima nor UTC for the whole class, so any accidental use of the server's zone fails here.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class FieldCheckinApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static TimeZone originalZone;

    @BeforeAll
    static void serverZoneIsNotLima() {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Auckland"));
    }

    @AfterAll
    static void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private RecyclerStatusCopyRepository copies;

    @Autowired
    private BlockedAssociationRepository blocked;

    @Autowired
    private FieldCheckinService checkins;

    @Autowired
    private JdbcTemplate jdbc;

    private static final Instant NOON = Instant.parse("2026-11-14T17:00:00Z");

    private String association;
    private String recycler;
    private String workday;
    private String neighbor;
    private String schedule;

    @BeforeEach
    void setUp() {
        association = UUID.randomUUID().toString();
        recycler = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, recycler, association, RecyclerStatus.ACTIVE);
        RestAssuredSetup.authenticated(port);
        neighbor = FieldFixtures.neighbor("Vecino CHECKIN", "150101");
        schedule = FieldFixtures.schedule(neighbor, "SATURDAY", "09:00:00");
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\"]}".formatted(schedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);
        RestAssuredSetup.anonymous(port);
        workday = as(recycler).body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(201).extract().path("workdayId");
    }

    private RequestSpecification as(String recyclerId) {
        return given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recyclerId, association))
                .contentType(ContentType.JSON);
    }

    private RequestSpecification asWithToken(String token) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON);
    }

    private String body(String clientId, String weight, String waste, Instant at, long seq, boolean withCoordinates) {
        String coordinates = withCoordinates ? "\"lat\": -12.0464, \"lng\": -77.0428, \"accuracyM\": 9.5," : "";
        return """
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", %s
                 "weightKg": %s, "wasteType": "%s", "deviceSeq": %d}
                """.formatted(clientId, neighbor, schedule, at, coordinates, weight, waste, seq);
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private long recordsOf(String recyclerId) {
        return count("select count(*) from collection_record where recycler_id = ?::uuid", recyclerId);
    }

    private long eventsFor(String recordId) {
        return count("select count(*) from outbox_event_collection where routing_key = 'collection.record.registered' and payload_json like ?",
                "%" + recordId + "%");
    }

    @Test
    void aCheckinCreatesOneOfficialRecordAndOneEventWithTheTokensIdentity() {
        String clientId = UUID.randomUUID().toString();

        var response = as(recycler).body(body(clientId, "12.50", "PET", NOON, 1, true))
                .post("/workdays/{id}/checkins", workday)
                .then().log().ifValidationFails().statusCode(201).body("checkinId", notNullValue()).body("recordId", notNullValue())
                .extract();
        String recordId = response.path("recordId");

        Map<String, Object> record = jdbc.queryForMap(
                "select neighbor_id::text n, schedule_id::text s, association_id::text a, recycler_id::text r, waste_type, weight_kg, collection_date from collection_record where id = ?::uuid",
                recordId);
        assertThat(record).containsEntry("n", neighbor).containsEntry("s", schedule)
                .containsEntry("a", association).containsEntry("r", recycler).containsEntry("waste_type", "PET");
        assertThat((BigDecimal) record.get("weight_kg")).isEqualByComparingTo("12.50");
        assertThat(count("select count(*) from field_checkin where record_id = ?::uuid", recordId)).isEqualTo(1);
        assertThat(eventsFor(recordId)).isEqualTo(1);
        String payload = jdbc.queryForObject(
                "select payload_json from outbox_event_collection where payload_json like ?", String.class, "%" + recordId + "%");
        assertThat(payload).contains("\"recyclerId\":\"" + recycler + "\"").contains("\"wasteType\":\"PET\"")
                .contains("\"districtUbigeo\":\"150101\"");
    }

    @Test
    void repeatingTheSameCheckinAnswers200WithTheSameIdsAndCreatesNothing() {
        String clientId = UUID.randomUUID().toString();
        String request = body(clientId, "3.5", "VIDRIO", NOON, 1, false);
        var first = as(recycler).body(request).post("/workdays/{id}/checkins", workday).then().statusCode(201).extract();

        long records = recordsOf(recycler);
        long outbox = count("select count(*) from outbox_event_collection");
        // "3.50" is the same weight as "3.5": still the same content.
        as(recycler).body(body(clientId, "3.50", "VIDRIO", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(200)
                .body("checkinId", equalTo(first.path("checkinId")))
                .body("recordId", equalTo(first.path("recordId")));

        assertThat(recordsOf(recycler)).isEqualTo(records);
        assertThat(count("select count(*) from outbox_event_collection")).isEqualTo(outbox);
        assertThat(count("select count(*) from field_checkin where recycler_id = ?::uuid", recycler)).isEqualTo(1);
    }

    @Test
    void theSameClientIdWithOtherContentIsCol020() {
        String clientId = UUID.randomUUID().toString();
        as(recycler).body(body(clientId, "3.5", "VIDRIO", NOON, 1, false)).post("/workdays/{id}/checkins", workday).then().statusCode(201);

        as(recycler).body(body(clientId, "4.0", "VIDRIO", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-020"));
        as(recycler).body(body(clientId, "3.5", "PET", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-020"));
        assertThat(recordsOf(recycler)).isEqualTo(1);
    }

    @Test
    void manySimultaneousRequestsWithTheSameClientIdLeaveOneCheckinAndOneRecord() throws Exception {
        String clientId = UUID.randomUUID().toString();
        String request = body(clientId, "7.25", "METAL", NOON, 1, false);
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return as(recycler).body(request).post("/workdays/{id}/checkins", workday).then().extract().statusCode();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).allMatch(s -> s == 200 || s == 201);
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from field_checkin where recycler_id = ?::uuid", recycler)).isEqualTo(1);
        assertThat(recordsOf(recycler)).isEqualTo(1);
        String recordId = jdbc.queryForObject("select record_id::text from field_checkin where recycler_id = ?::uuid", String.class, recycler);
        assertThat(eventsFor(recordId)).as("one event for the one record").isEqualTo(1);
    }

    @Test
    void ifTheCheckinRowCannotBeWrittenNeitherTheRecordNorTheEventRemain() {
        jdbc.execute("""
                create function it_fail_checkin() returns trigger language plpgsql as
                $$ begin raise exception 'forced failure of the check-in insert'; end $$
                """);
        jdbc.execute("create trigger it_fail_checkin before insert on field_checkin for each row execute function it_fail_checkin()");
        long outbox = count("select count(*) from outbox_event_collection where routing_key = 'collection.record.registered'");
        try {
            as(recycler).body(body(UUID.randomUUID().toString(), "5", "PAPEL", NOON, 1, false))
                    .post("/workdays/{id}/checkins", workday)
                    .then().statusCode(org.hamcrest.Matchers.greaterThanOrEqualTo(500));
        } finally {
            jdbc.execute("drop trigger it_fail_checkin on field_checkin");
            jdbc.execute("drop function it_fail_checkin()");
        }
        assertThat(recordsOf(recycler)).as("single transaction: no record without its check-in").isZero();
        assertThat(count("select count(*) from outbox_event_collection where routing_key = 'collection.record.registered'")).isEqualTo(outbox);
        assertThat(count("select count(*) from field_checkin where recycler_id = ?::uuid", recycler)).isZero();
    }

    @Test
    void collectionDateIsTheDateInLimaNotInUtcNorInTheServersZone() {
        // 23:30 in Lima on 14 Nov is 04:30 UTC on 15 Nov; 00:30 in Lima on 15 Nov is 05:30 UTC.
        String late = as(recycler).body(body(UUID.randomUUID().toString(), "1", "PET", Instant.parse("2026-11-15T04:30:00Z"), 1, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("recordId");
        String after = as(recycler).body(body(UUID.randomUUID().toString(), "1", "PET", Instant.parse("2026-11-15T05:30:00Z"), 2, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("recordId");

        assertThat(dateOf(late)).isEqualTo(LocalDate.parse("2026-11-14"));
        assertThat(dateOf(after)).isEqualTo(LocalDate.parse("2026-11-15"));
        assertThat(TimeZone.getDefault().getID()).as("the server zone really is not Lima").isEqualTo("Pacific/Auckland");
        // Stored as UTC instants, whatever the server zone.
        assertThat(jdbc.queryForObject("select recorded_at from field_checkin where record_id = ?::uuid", java.time.OffsetDateTime.class, late)
                        .toInstant())
                .isEqualTo(Instant.parse("2026-11-15T04:30:00Z"));
    }

    private LocalDate dateOf(String recordId) {
        return jdbc.queryForObject("select collection_date from collection_record where id = ?::uuid", LocalDate.class, recordId);
    }

    @Test
    void checkinsComeBackOrderedByDeviceSeqWhenThePhoneClockWentBack() {
        as(recycler).body(body(UUID.randomUUID().toString(), "1", "PET", NOON.plusSeconds(600), 1, false)).post("/workdays/{id}/checkins", workday).then().statusCode(201);
        as(recycler).body(body(UUID.randomUUID().toString(), "1", "PET", NOON, 2, false)).post("/workdays/{id}/checkins", workday).then().statusCode(201);
        as(recycler).body(body(UUID.randomUUID().toString(), "1", "PET", NOON.minusSeconds(3600), 3, false)).post("/workdays/{id}/checkins", workday).then().statusCode(201);

        assertThat(checkins.checkinsInOrder(UUID.fromString(workday)))
                .extracting(FieldCheckinService.StoredCheckin::deviceSeq)
                .containsExactly(1L, 2L, 3L);
    }

    @Test
    void aCheckinRecordedBeforeAnyRevocationIsStillAccepted() {
        // CA-26 server side: nothing about the phone's earlier revoked device matters; only the live token does.
        as(recycler).body(body(UUID.randomUUID().toString(), "2", "TEXTIL", Instant.parse("2026-10-01T15:00:00Z"), 1, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201);
    }

    @Test
    void aNeighbourWhoIsInactiveIsCol013AndABlockedAssociationIsCol009() {
        RestAssuredSetup.authenticated(port);
        String other = FieldFixtures.neighbor("Vecino INACTIVO", "150101");
        String otherSchedule = FieldFixtures.schedule(other, "SUNDAY", "09:00:00");
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\", \"%s\"]}".formatted(schedule, otherSchedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);
        FieldFixtures.deactivateNeighbor(other);
        RestAssuredSetup.anonymous(port);

        as(recycler).body("""
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": 2, "wasteType": "PET", "deviceSeq": 1}
                """.formatted(UUID.randomUUID(), other, otherSchedule, NOON)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-013"));

        blocked.block(BlockedAssociation.block(UUID.fromString(association), Instant.now()));
        try {
            as(recycler).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 2, false)).post("/workdays/{id}/checkins", workday)
                    .then().statusCode(409).body("code", equalTo("COL-009"));
        } finally {
            blocked.unblock(UUID.fromString(association));
        }
        assertThat(recordsOf(recycler)).isZero();
    }

    @Test
    void aScheduleNotAssignedToThisRecyclerOrNotThatNeighboursIsCol015() {
        String stranger = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, stranger, association, RecyclerStatus.ACTIVE);
        String strangerWorkday = as(stranger).body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(201).extract().path("workdayId");
        // Another recycler tries the first recycler's schedule.
        as(stranger).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 1, false)).post("/workdays/{id}/checkins", strangerWorkday)
                .then().statusCode(403).body("code", equalTo("COL-015"));
        // The right recycler, a schedule of ANOTHER neighbour.
        RestAssuredSetup.authenticated(port);
        String otherNeighbor = FieldFixtures.neighbor("Otro vecino", "150101");
        String otherSchedule = FieldFixtures.schedule(otherNeighbor, "SUNDAY", "10:00:00");
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\", \"%s\"]}".formatted(schedule, otherSchedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);
        RestAssuredSetup.anonymous(port);
        as(recycler).body("""
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": 2, "wasteType": "PET", "deviceSeq": 1}
                """.formatted(UUID.randomUUID(), neighbor, otherSchedule, NOON)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(403).body("code", equalTo("COL-015"));
        assertThat(recordsOf(recycler)).isZero();
        assertThat(recordsOf(stranger)).isZero();
    }

    @Test
    void invalidWeightsAndWasteTypesAreRejected() {
        for (String weight : List.of("0", "-1", "2.555")) {
            as(recycler).body(body(UUID.randomUUID().toString(), weight, "PET", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                    .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        }
        as(recycler).body(body(UUID.randomUUID().toString(), "2", "ORGANICO", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(400).body("code", equalTo("COL-014"));
        assertThat(recordsOf(recycler)).isZero();
    }

    @Test
    void coordinatesNeedTheLocationConsentButACheckinWithoutThemDoesNot() {
        String noLocation = TestJwtTokens.recyclerToken(recycler, association, 1, false);
        asWithToken(noLocation).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 1, true)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("AUTH-019"));
        String id = asWithToken(noLocation).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 2, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(201).extract().path("checkinId");

        assertThat(jdbc.queryForObject("select lat is null and lng is null and accuracy_m is null from field_checkin where id = ?::uuid", Boolean.class, id)).isTrue();
        assertThat(recordsOf(recycler)).isEqualTo(1);
    }

    @Test
    void partialCoordinatesAreRejected() {
        String partial = """
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "lat": -12.0,
                 "weightKg": 2, "wasteType": "PET", "deviceSeq": 1}
                """.formatted(UUID.randomUUID(), neighbor, schedule, NOON);
        as(recycler).body(partial).post("/workdays/{id}/checkins", workday).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void anotherRecyclersWorkdayIsCol016AndAClosedOneBeyondTheToleranceIsCol017() {
        String stranger = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, stranger, association, RecyclerStatus.ACTIVE);
        as(stranger).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(404).body("code", equalTo("COL-016"));

        as(recycler).body("{\"endedAt\": \"2026-11-14T20:00:00Z\"}").post("/workdays/{id}/close", workday).then().statusCode(200);
        as(recycler).body(body(UUID.randomUUID().toString(), "2", "PET", Instant.parse("2026-11-14T20:04:00Z"), 1, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201);
        as(recycler).body(body(UUID.randomUUID().toString(), "2", "PET", Instant.parse("2026-11-14T20:06:00Z"), 2, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(409).body("code", equalTo("COL-017"));
        as(recycler).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 3, false))
                .post("/workdays/{id}/checkins", UUID.randomUUID()).then().statusCode(404).body("code", equalTo("COL-016"));
    }

    @Test
    void aRecyclerWithoutActiveCopyIsCol019AndStaffIsAuth000() {
        String unknown = UUID.randomUUID().toString();
        as(unknown).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 1, false)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(403).body("code", equalTo("COL-019"));
        RestAssuredSetup.authenticated(port);
        given().contentType(ContentType.JSON).body(body(UUID.randomUUID().toString(), "2", "PET", NOON, 1, false))
                .post("/workdays/{id}/checkins", workday).then().statusCode(403).body("code", equalTo("AUTH-000"));
    }

    @Test
    void aPhotoKeyCannotBeAcceptedUntilItsObjectCanBeVerified() {
        String withPhoto = """
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": 2,
                 "wasteType": "PET", "deviceSeq": 1, "photoKey": "evidence/x/2026/y.jpg"}
                """.formatted(UUID.randomUUID(), neighbor, schedule, NOON);
        as(recycler).body(withPhoto).post("/workdays/{id}/checkins", workday).then().statusCode(409).body("code", equalTo("COL-018"));
        assertThat(recordsOf(recycler)).isZero();
    }
}

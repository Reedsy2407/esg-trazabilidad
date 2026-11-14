package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import pe.esgtrazabilidad.collection.fieldwork.WorkdayService;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/** CA-33 and CA-34 (and the ordering half of CA-47): workday opening, closing and GPS points. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class WorkdayApiIT {

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
    private WorkdayService workdays;

    @Autowired
    private JdbcTemplate jdbc;

    private final String association = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        RestAssuredSetup.anonymous(port);
    }

    private String recycler() {
        String id = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, id, association, RecyclerStatus.ACTIVE);
        return id;
    }

    private RequestSpecification as(String recyclerId) {
        return given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recyclerId, association))
                .contentType(ContentType.JSON);
    }

    private String open(String recycler, String clientId, Instant startedAt) {
        return as(recycler)
                .body("{\"startedAt\": \"%s\", \"clientId\": \"%s\"}".formatted(startedAt, clientId))
                .post("/workdays")
                .then()
                .statusCode(201)
                .extract()
                .path("workdayId");
    }

    private static String point(long seq, Instant at) {
        return "{\"lat\": -12.0464, \"lng\": -77.0428, \"accuracyM\": 8.5, \"recordedAt\": \"%s\", \"deviceSeq\": %d}".formatted(at, seq);
    }

    private static String batch(String... points) {
        return "{\"points\": [" + String.join(",", points) + "]}";
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    @Test
    void openingIsIdempotentByClientIdAndCreatesOnce() {
        String r = recycler();
        String clientId = UUID.randomUUID().toString();
        Instant start = Instant.parse("2026-11-14T13:00:00Z");

        String first = open(r, clientId, start);
        as(r).body("{\"startedAt\": \"%s\", \"clientId\": \"%s\"}".formatted(start, clientId))
                .post("/workdays")
                .then()
                .statusCode(200)
                .body("workdayId", equalTo(first));

        assertThat(count("select count(*) from workday where recycler_id = ?::uuid and client_id = ?::uuid", r, clientId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select with_gps from workday where id = ?::uuid", Boolean.class, first)).isTrue();
        assertThat(jdbc.queryForObject("select association_id::text from workday where id = ?::uuid", String.class, first))
                .isEqualTo(association);
    }

    @Test
    void twoSimultaneousOpeningsLeaveOneWorkdayAndNeitherFails() throws Exception {
        String r = recycler();
        String clientId = UUID.randomUUID().toString();
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    ready.countDown();
                    go.await();
                    return as(r).body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(clientId))
                            .post("/workdays")
                            .then()
                            .extract()
                            .statusCode();
                };
                results.add(pool.submit(call));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get(60, TimeUnit.SECONDS));
            }
            assertThat(statuses).allMatch(s -> s == 200 || s == 201);
            assertThat(statuses.stream().filter(s -> s == 201).count()).as("exactly one creation").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("select count(*) from workday where recycler_id = ?::uuid and client_id = ?::uuid", r, clientId)).isEqualTo(1);
    }

    @Test
    void aRecyclerNeitherSeesNorTouchesAnotherOnesWorkday() {
        String owner = recycler();
        String other = recycler();
        String id = open(owner, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));

        as(other).body("{\"endedAt\": \"2026-11-14T20:00:00Z\"}").post("/workdays/{id}/close", id)
                .then().statusCode(404).body("code", equalTo("COL-016"));
        as(other).body(batch(point(1, Instant.parse("2026-11-14T13:05:00Z")))).post("/workdays/{id}/points", id)
                .then().statusCode(404).body("code", equalTo("COL-016"));
        as(owner).body("{\"endedAt\": \"2026-11-14T20:00:00Z\"}").post("/workdays/{id}/close", UUID.randomUUID())
                .then().statusCode(404).body("code", equalTo("COL-016"));
        assertThat(jdbc.queryForObject("select status from workday where id = ?::uuid", String.class, id)).isEqualTo("OPEN");
    }

    @Test
    void closingTwiceIsASelfTransitionThatKeepsTheFirstEnd() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));

        as(r).body("{\"endedAt\": \"2026-11-14T20:00:00Z\"}").post("/workdays/{id}/close", id)
                .then().statusCode(200).body("status", equalTo("CLOSED")).body("endedAt", notNullValue());
        as(r).body("{\"endedAt\": \"2026-11-14T23:00:00Z\"}").post("/workdays/{id}/close", id)
                .then().statusCode(200).body("status", equalTo("CLOSED"));

        assertThat(jdbc.queryForObject("select ended_at from workday where id = ?::uuid", java.time.OffsetDateTime.class, id)
                        .toInstant())
                .isEqualTo(Instant.parse("2026-11-14T20:00:00Z"));
    }

    @Test
    void duplicatePointsAreIgnoredAndCounted() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        Instant t0 = Instant.parse("2026-11-14T13:01:00Z");

        as(r).body(batch(point(1, t0), point(2, t0.plusSeconds(10)), point(2, t0.plusSeconds(10))))
                .post("/workdays/{id}/points", id)
                .then().statusCode(200).body("accepted", equalTo(2)).body("duplicates", equalTo(1));
        as(r).body(batch(point(2, t0.plusSeconds(10)), point(3, t0.plusSeconds(20))))
                .post("/workdays/{id}/points", id)
                .then().statusCode(200).body("accepted", equalTo(1)).body("duplicates", equalTo(1));

        assertThat(workdays.pointsInOrder(UUID.fromString(id))).hasSize(3);
    }

    @Test
    void pointsComeBackOrderedByDeviceSeqNotByArrivalOrClock() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        Instant t0 = Instant.parse("2026-11-14T13:01:00Z");
        // The phone's clock jumped back in the middle: seq 3 carries the earliest recordedAt.
        as(r).body(batch(point(3, t0), point(1, t0.plusSeconds(60)), point(2, t0.plusSeconds(120))))
                .post("/workdays/{id}/points", id).then().statusCode(200);
        as(r).body(batch(point(4, t0.plusSeconds(30)))).post("/workdays/{id}/points", id).then().statusCode(200);

        assertThat(workdays.pointsInOrder(UUID.fromString(id)))
                .extracting(WorkdayService.StoredPoint::deviceSeq)
                .containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    void theBatchLimitIs500() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        Instant t0 = Instant.parse("2026-11-14T13:01:00Z");
        List<String> points = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            points.add(point(i, t0.plusSeconds(i)));
        }

        as(r).body(batch(points.toArray(String[]::new))).post("/workdays/{id}/points", id)
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        as(r).body(batch(points.subList(0, 500).toArray(String[]::new))).post("/workdays/{id}/points", id)
                .then().statusCode(200).body("accepted", equalTo(500));
        assertThat(workdays.pointsInOrder(UUID.fromString(id))).hasSize(500);
    }

    @Test
    void aClosedWorkdayTakesPointsUpToFiveMinutesAfterTheEndThenCol017() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        Instant end = Instant.parse("2026-11-14T20:00:00Z");
        as(r).body("{\"endedAt\": \"%s\"}".formatted(end)).post("/workdays/{id}/close", id).then().statusCode(200);

        as(r).body(batch(point(1, end.minusSeconds(30)), point(2, end.plusSeconds(4 * 60 + 59)), point(5, end.plusSeconds(5 * 60))))
                .post("/workdays/{id}/points", id)
                .then().statusCode(200).body("accepted", equalTo(3));
        as(r).body(batch(point(3, end.plusSeconds(60)), point(4, end.plusSeconds(5 * 60 + 1))))
                .post("/workdays/{id}/points", id)
                .then().statusCode(409).body("code", equalTo("COL-017"));

        assertThat(workdays.pointsInOrder(UUID.fromString(id))).as("the refused batch stored nothing").hasSize(3);
    }

    @Test
    void theSameBatchSentFromManyThreadsIsStoredOnceAndNeverFails() throws Exception {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        Instant t0 = Instant.parse("2026-11-14T13:01:00Z");
        String body = batch(point(1, t0), point(2, t0.plusSeconds(1)), point(3, t0.plusSeconds(2)), point(4, t0.plusSeconds(3)));
        int threads = 6;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<int[]>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    var response = as(r).body(body).post("/workdays/{id}/points", id).then().extract();
                    return new int[] {response.statusCode(), response.path("accepted"), response.path("duplicates")};
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int accepted = 0;
            int duplicates = 0;
            for (Future<int[]> f : results) {
                int[] result = f.get(60, TimeUnit.SECONDS);
                assertThat(result[0]).as("a lost race is 'already exists', never an error").isEqualTo(200);
                accepted += result[1];
                duplicates += result[2];
            }
            assertThat(accepted).as("each point stored by exactly one of the requests").isEqualTo(4);
            assertThat(duplicates).isEqualTo(4 * (threads - 1));
        } finally {
            pool.shutdownNow();
        }
        assertThat(workdays.pointsInOrder(UUID.fromString(id))).hasSize(4);
    }

    @Test
    void pointsNeedTheLocationConsentButOpeningAndClosingDoNot() {
        String r = recycler();
        String noLocation = TestJwtTokens.recyclerToken(r, association, 1, false);
        String clientId = UUID.randomUUID().toString();
        String id = given().header("Authorization", "Bearer " + noLocation)
                .contentType(ContentType.JSON)
                .body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(clientId))
                .post("/workdays")
                .then().statusCode(201).extract().path("workdayId");

        given().header("Authorization", "Bearer " + noLocation)
                .contentType(ContentType.JSON)
                .body(batch(point(1, Instant.parse("2026-11-14T13:01:00Z"))))
                .post("/workdays/{id}/points", id)
                .then().statusCode(409).body("code", equalTo("AUTH-019"));
        given().header("Authorization", "Bearer " + noLocation)
                .contentType(ContentType.JSON)
                .body("{\"endedAt\": \"2026-11-14T20:00:00Z\"}")
                .post("/workdays/{id}/close", id)
                .then().statusCode(200);

        assertThat(jdbc.queryForObject("select with_gps from workday where id = ?::uuid", Boolean.class, id)).isFalse();
        assertThat(workdays.pointsInOrder(UUID.fromString(id))).isEmpty();
    }

    @Test
    void noConsentAtAllIsAuth019OnEveryFieldRoute() {
        String r = recycler();
        String noConsent = TestJwtTokens.recyclerToken(r, association, null, false);
        given().header("Authorization", "Bearer " + noConsent).contentType(ContentType.JSON)
                .body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(409).body("code", equalTo("AUTH-019"));
    }

    @Test
    void anInactiveRecyclerOrOneWithoutCopyCannotWriteCol019() {
        String inactive = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, inactive, association, RecyclerStatus.INACTIVE);
        for (String r : List.of(inactive, UUID.randomUUID().toString())) {
            as(r).body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                    .post("/workdays").then().statusCode(403).body("code", equalTo("COL-019"));
        }
    }

    @Test
    void staffCannotUseTheWorkdayRoutes() {
        RestAssuredSetup.authenticated(port);
        given().contentType(ContentType.JSON)
                .body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(403).body("code", equalTo("AUTH-000"));
    }

    @Test
    void invalidPointsAreRejectedWith400() {
        String r = recycler();
        String id = open(r, UUID.randomUUID().toString(), Instant.parse("2026-11-14T13:00:00Z"));
        String badLat = "{\"lat\": 91, \"lng\": -77, \"accuracyM\": 5, \"recordedAt\": \"2026-11-14T13:01:00Z\", \"deviceSeq\": 1}";
        String noSeq = "{\"lat\": -12, \"lng\": -77, \"accuracyM\": 5, \"recordedAt\": \"2026-11-14T13:01:00Z\"}";
        for (String bad : List.of(badLat, noSeq)) {
            as(r).body(batch(bad)).post("/workdays/{id}/points", id).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        }
    }
}

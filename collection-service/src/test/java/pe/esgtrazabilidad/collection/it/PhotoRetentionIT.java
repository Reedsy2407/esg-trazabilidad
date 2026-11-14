package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.InMemoryEvidenceStorage;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.collection.retention.RetentionRunner;
import pe.esgtrazabilidad.collection.retention.RetentionRunner.RetentionRunResult;
import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy;

/** CA-40: photos older than the period are deleted from the bucket; the record and its totals do not change. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
@Import(InMemoryEvidenceStorage.Config.class)
class PhotoRetentionIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /** The clock of the whole class: the job is told what time it is, nothing reads the real one. */
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private RecyclerStatusCopyRepository copies;

    @Autowired
    private InMemoryEvidenceStorage bucket;

    @Autowired
    private RetentionRunner runner;

    @Autowired
    private RetentionSettingsCopy settings;

    @Autowired
    private JdbcTemplate jdbc;

    private String association;
    private String recycler;
    private String workday;
    private String neighbor;
    private String schedule;
    private int sequence;

    @BeforeEach
    void setUp() {
        bucket.reset();
        jdbc.update("delete from retention_settings_copy");
        // The database is shared by the tests of this class: photos left by earlier ones are not this test's.
        jdbc.update("update field_checkin set photo_deleted_at = now() where photo_key is not null and photo_deleted_at is null");
        association = UUID.randomUUID().toString();
        recycler = UUID.randomUUID().toString();
        sequence = 0;
        FieldFixtures.recyclerCopy(copies, recycler, association, RecyclerStatus.ACTIVE);
        RestAssuredSetup.authenticated(port);
        neighbor = FieldFixtures.neighbor("Vecino RETENCION", "150101");
        schedule = FieldFixtures.schedule(neighbor, "SATURDAY", "09:00:00");
        given().contentType(ContentType.JSON).body("{\"scheduleIds\": [\"%s\"]}".formatted(schedule))
                .put("/recyclers/{id}/assignment", recycler).then().statusCode(200);
        RestAssuredSetup.anonymous(port);
        workday = as().body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(201).extract().path("workdayId");
    }

    private RequestSpecification as() {
        return given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recycler, association))
                .contentType(ContentType.JSON);
    }

    private record Checkin(String id, String recordId, String key) {
    }

    /** A check-in with a photo already in the bucket, received {@code monthsAgo} months before NOW. */
    private Checkin checkinWithPhoto(int monthsAgo) {
        String clientId = UUID.randomUUID().toString();
        String key = as().body("{\"checkinClientId\": \"%s\", \"contentType\": \"image/jpeg\", \"sizeBytes\": 1000, \"sha256\": \"%s\"}"
                        .formatted(clientId, "b".repeat(64)))
                .post("/evidence/presign").then().statusCode(200).extract().path("key");
        bucket.put(key, 1000);
        Map<String, Object> created = as().body("""
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "2026-11-14T17:00:00Z", "weightKg": 2.5,
                 "wasteType": "PET", "deviceSeq": %d, "photoKey": "%s"}
                """.formatted(clientId, neighbor, schedule, ++sequence, key))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("$");
        String checkinId = (String) created.get("checkinId");
        jdbc.update("update field_checkin set received_at = ? where id = ?::uuid",
                Timestamp.from(NOW.atOffset(ZoneOffset.UTC).minusMonths(monthsAgo).toInstant()), checkinId);
        return new Checkin(checkinId, (String) created.get("recordId"), key);
    }

    private Instant deletedAt(String checkinId) {
        Timestamp stamp = jdbc.queryForObject("select photo_deleted_at from field_checkin where id = ?::uuid", Timestamp.class, checkinId);
        return stamp == null ? null : stamp.toInstant();
    }

    @Test
    void photosOlderThanThePeriodAreDeletedFromTheBucketAndMarkedAndTheRecordDoesNotChange() {
        Checkin old = checkinWithPhoto(13);
        Checkin recent = checkinWithPhoto(11);
        Map<String, Object> recordBefore = jdbc.queryForMap("select * from collection_record where id = ?::uuid", old.recordId());
        Map<String, Object> checkinBefore = jdbc.queryForMap(
                "select neighbor_id, schedule_id, recorded_at, received_at, waste_type, photo_key, content_hash from field_checkin where id = ?::uuid",
                old.id());

        RetentionRunResult result = runner.run(NOW);

        assertThat(result.photoMonths()).isEqualTo(12);
        assertThat(result.photosDeleted()).isEqualTo(1);
        assertThat(result.photosFailed()).isZero();
        assertThat(bucket.contains(old.key())).as("the old photo is gone from the bucket").isFalse();
        assertThat(bucket.contains(recent.key())).as("the recent one stays").isTrue();
        assertThat(deletedAt(old.id())).isEqualTo(NOW);
        assertThat(deletedAt(recent.id())).isNull();
        // The record, its weight and its photo key (the history that it had one) are untouched.
        assertThat(jdbc.queryForMap("select * from collection_record where id = ?::uuid", old.recordId())).isEqualTo(recordBefore);
        assertThat(jdbc.queryForMap(
                        "select neighbor_id, schedule_id, recorded_at, received_at, waste_type, photo_key, content_hash from field_checkin where id = ?::uuid",
                        old.id()))
                .isEqualTo(checkinBefore);
    }

    @Test
    void runningTwiceInARowDoesNothingTheSecondTime() {
        Checkin old = checkinWithPhoto(14);
        runner.run(NOW);
        List<String> deletedAfterFirst = bucket.deleted();
        Instant markedAt = deletedAt(old.id());

        RetentionRunResult second = runner.run(NOW.plusSeconds(3600));

        assertThat(second.photosDeleted()).isZero();
        assertThat(bucket.deleted()).isEqualTo(deletedAfterFirst);
        assertThat(deletedAt(old.id())).as("the mark keeps its first instant").isEqualTo(markedAt);
    }

    @Test
    void ifTheBucketFailsTheRowIsNotMarkedAndTheNextRunRetriesIt() {
        Checkin old = checkinWithPhoto(13);
        bucket.failNextDeletes(1);

        RetentionRunResult first = runner.run(NOW);

        assertThat(first.photosDeleted()).isZero();
        assertThat(first.photosFailed()).isEqualTo(1);
        assertThat(deletedAt(old.id())).as("not marked: the photo may still be there").isNull();
        assertThat(bucket.contains(old.key())).isTrue();

        RetentionRunResult second = runner.run(NOW);

        assertThat(second.photosDeleted()).isEqualTo(1);
        assertThat(second.photosFailed()).isZero();
        assertThat(bucket.contains(old.key())).isFalse();
        assertThat(deletedAt(old.id())).isEqualTo(NOW);
    }

    @Test
    void oneFailingPhotoDoesNotStopTheOthersNorTheGpsHalf() {
        Checkin first = checkinWithPhoto(13);
        Checkin second = checkinWithPhoto(15);
        bucket.failNextDeletes(1);

        RetentionRunResult result = runner.run(NOW);

        assertThat(result.photosDeleted()).isEqualTo(1);
        assertThat(result.photosFailed()).isEqualTo(1);
        assertThat(java.util.Arrays.asList(deletedAt(first.id()), deletedAt(second.id())))
                .as("exactly one of the two was marked; the other waits for the next run")
                .filteredOn(java.util.Objects::nonNull)
                .hasSize(1);
    }

    @Test
    void theBucketIsNeverCalledInsideADatabaseTransaction() {
        checkinWithPhoto(13);
        checkinWithPhoto(14);
        bucket.reset();
        // reset() cleared the objects too; the deletes still happen (deleting a missing object is harmless).

        runner.run(NOW);

        assertThat(bucket.callsInsideTransaction()).hasSize(2).containsOnly(false);
    }

    @Test
    void severalRunnersAtTheSameTimeMarkEachPhotoOnceAndTheTotalsAddUp() throws Exception {
        for (int i = 0; i < 10; i++) {
            checkinWithPhoto(13);
        }
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int deleted = 0;
        int failed = 0;
        try {
            List<Future<RetentionRunResult>> runs = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                runs.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return runner.run(NOW);
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            for (Future<RetentionRunResult> run : runs) {
                RetentionRunResult result = run.get(60, TimeUnit.SECONDS);
                deleted += result.photosDeleted();
                failed += result.photosFailed();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(deleted).as("each photo counted by exactly one runner").isEqualTo(10);
        assertThat(failed).isZero();
        assertThat(jdbc.queryForObject("select count(*) from field_checkin where recycler_id = ?::uuid and photo_deleted_at is not null", Long.class, recycler))
                .isEqualTo(10L);
    }

    @Test
    void thePeriodAnnouncedByAuthAppliesToTheNextRun() {
        Checkin fiveMonthsOld = checkinWithPhoto(5);
        runner.run(NOW);
        assertThat(bucket.contains(fiveMonthsOld.key())).as("default 12 months: kept").isTrue();

        settings.applyIfNewer(90, 3, 2, NOW.minusSeconds(60));
        RetentionRunResult result = runner.run(NOW);

        assertThat(result.photoMonths()).isEqualTo(3);
        assertThat(result.photosDeleted()).isEqualTo(1);
        assertThat(bucket.contains(fiveMonthsOld.key())).isFalse();
    }

    @Test
    void checkinsWithoutAPhotoAreNotTouched() {
        String clientId = UUID.randomUUID().toString();
        String checkinId = as().body("""
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "2026-11-14T17:00:00Z", "weightKg": 2.5,
                 "wasteType": "PET", "deviceSeq": 1}
                """.formatted(clientId, neighbor, schedule))
                .post("/workdays/{id}/checkins", workday).then().statusCode(201).extract().path("checkinId");
        jdbc.update("update field_checkin set received_at = ? where id = ?::uuid",
                Timestamp.from(NOW.atOffset(ZoneOffset.UTC).minusMonths(20).toInstant()), checkinId);

        RetentionRunResult result = runner.run(NOW);

        assertThat(result.photosDeleted()).isZero();
        assertThat(result.photosFailed()).isZero();
        assertThat(bucket.deleted()).isEmpty();
        assertThat(deletedAt(checkinId)).isNull();
    }
}

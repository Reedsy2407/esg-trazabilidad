package pe.esgtrazabilidad.collection.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.retention.RetentionRunner.RetentionRunResult;

/** CA-39: GPS points older than the periods become a route summary; the job is idempotent and claim-safe. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = "esg.events.outbox-dispatcher.enabled=false")
class GpsRetentionIT {

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

    @Autowired
    private RetentionRunner runner;

    @Autowired
    private GpsRetention gps;

    @Autowired
    private RetentionSettingsCopy settings;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanSlate() {
        jdbc.update("delete from route_summary");
        jdbc.update("delete from workday_point");
        jdbc.update("delete from workday");
        jdbc.update("delete from retention_settings_copy");
    }

    private UUID workday() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into workday (id, recycler_id, association_id, client_id, status, started_at, received_at, with_gps) "
                        + "values (?, ?, ?, ?, 'CLOSED', ?, ?, true)",
                id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Timestamp.from(NOW.minus(Duration.ofDays(200))), Timestamp.from(NOW));
        return id;
    }

    private void point(UUID workday, Instant at, long seq, double lat, double lng, double accuracy) {
        jdbc.update(
                "insert into workday_point (workday_id, recorded_at, device_seq, lat, lng, accuracy_m, received_at) values (?, ?, ?, ?, ?, ?, ?)",
                workday, Timestamp.from(at), seq, lat, lng, accuracy, Timestamp.from(NOW));
    }

    /** A straight walk south, a point every 20 s starting {@code days} days before NOW. */
    private UUID walk(int days, int points) {
        UUID id = workday();
        Instant start = NOW.minus(Duration.ofDays(days));
        for (int i = 0; i < points; i++) {
            point(id, start.plusSeconds(i * 20L), i, -12.0 - i * 0.0001, -77.0, 8.0);
        }
        return id;
    }

    private long count(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    @Test
    void oldPointsBecomeASummaryAndAreDeleted() {
        UUID id = walk(100, 60);

        RetentionRunResult result = runner.run(NOW);

        assertThat(result.gpsDays()).isEqualTo(90);
        assertThat(result.workdaysSummarized()).isEqualTo(1);
        assertThat(result.pointsDeleted()).isEqualTo(60);
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isZero();
        Map<String, Object> summary = jdbc.queryForMap(
                "select point_count, distance_m, first_at, last_at, polyline from route_summary where workday_id = ?", id);
        assertThat(summary.get("point_count")).isEqualTo(60);
        // 59 legs of 0.0001 degrees of latitude (about 11.1 m each).
        assertThat((Double) summary.get("distance_m")).isCloseTo(59 * 11.12, within(15.0));
        assertThat(((Timestamp) summary.get("first_at")).toInstant()).isEqualTo(NOW.minus(Duration.ofDays(100)));
        assertThat(((Timestamp) summary.get("last_at")).toInstant()).isEqualTo(NOW.minus(Duration.ofDays(100)).plusSeconds(59 * 20L));
        assertThat((String) summary.get("polyline")).startsWith("[[-12.00000,-77.00000]").endsWith("[-12.00590,-77.00000]]");
    }

    @Test
    void runningTwiceInARowChangesNothingTheSecondTime() {
        UUID id = walk(100, 30);
        runner.run(NOW);
        Map<String, Object> before = jdbc.queryForMap("select * from route_summary where workday_id = ?", id);

        RetentionRunResult second = runner.run(NOW);

        assertThat(second.workdaysSummarized()).isZero();
        assertThat(second.pointsDeleted()).isZero();
        assertThat(jdbc.queryForMap("select * from route_summary where workday_id = ?", id)).isEqualTo(before);
        assertThat(count("select count(*) from route_summary")).isEqualTo(1);
    }

    @Test
    void aWorkdayWithAnyRecentPointIsLeftWhole() {
        UUID id = walk(100, 10);
        point(id, NOW.minus(Duration.ofDays(5)), 99, -12.5, -77.0, 8.0);

        RetentionRunResult result = runner.run(NOW);

        assertThat(result.workdaysSummarized()).isZero();
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isEqualTo(11);
        assertThat(count("select count(*) from route_summary")).isZero();
    }

    @Test
    void theBoundaryIsTheNumberOfDaysOfTheTermsInForce() {
        UUID eightyNine = walk(89, 5);
        UUID ninetyOne = walk(91, 5);

        runner.run(NOW);

        assertThat(count("select count(*) from workday_point where workday_id = ?", eightyNine)).as("89 days old: kept").isEqualTo(5);
        assertThat(count("select count(*) from workday_point where workday_id = ?", ninetyOne)).as("91 days old: summarised").isZero();
    }

    @Test
    void theTermsAnnouncedByAuthApplyToTheNextRunAndASilentServiceUsesNinety() {
        UUID fortyDaysOld = walk(40, 5);
        runner.run(NOW);
        assertThat(count("select count(*) from workday_point where workday_id = ?", fortyDaysOld)).as("default 90 days: kept").isEqualTo(5);

        settings.applyIfNewer(30, 12, 2, NOW.minusSeconds(60));
        RetentionRunResult result = runner.run(NOW);

        assertThat(result.gpsDays()).isEqualTo(30);
        assertThat(count("select count(*) from workday_point where workday_id = ?", fortyDaysOld)).as("30 days: summarised").isZero();
        assertThat(count("select count(*) from route_summary where workday_id = ?", fortyDaysOld)).isEqualTo(1);
    }

    @Test
    void pointsOfPoorAccuracyCountButDoNotShapeTheDistanceNorThePolyline() {
        UUID id = workday();
        Instant start = NOW.minus(Duration.ofDays(100));
        point(id, start, 0, -12.000, -77.0, 10);
        point(id, start.plusSeconds(20), 1, -12.500, -77.5, 500); // a wild jump, but a poor reading
        point(id, start.plusSeconds(40), 2, -12.001, -77.0, 10);

        runner.run(NOW);

        Map<String, Object> summary = jdbc.queryForMap("select point_count, distance_m, polyline from route_summary where workday_id = ?", id);
        assertThat(summary.get("point_count")).isEqualTo(3);
        assertThat((Double) summary.get("distance_m")).isCloseTo(111.2, within(2.0));
        assertThat((String) summary.get("polyline")).doesNotContain("-12.50000");
    }

    @Test
    void aLongWalkKeepsAtMostTwoHundredPolylinePoints() {
        UUID id = workday();
        Instant start = NOW.minus(Duration.ofDays(100));
        for (int i = 0; i < 1_500; i++) {
            point(id, start.plusSeconds(i), i, -12.0 - i * 0.0002, -77.0 + (i % 2 == 0 ? 0 : 0.002), 5);
        }

        runner.run(NOW);

        String polyline = jdbc.queryForObject("select polyline from route_summary where workday_id = ?", String.class, id);
        assertThat(polyline.split("\\],\\[").length).isLessThanOrEqualTo(200);
        assertThat(count("select point_count from route_summary where workday_id = ?", id)).isEqualTo(1_500);
    }

    @Test
    void twoRunnersAtTheSameTimeSummariseEachWorkdayOnce() throws Exception {
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            ids.add(walk(100 + i, 20));
        }
        int threads = 4;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        int summarized = 0;
        int deleted = 0;
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
                summarized += result.workdaysSummarized();
                deleted += result.pointsDeleted();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(summarized).as("each workday was claimed by exactly one runner").isEqualTo(8);
        assertThat(deleted).isEqualTo(8 * 20);
        assertThat(count("select count(*) from route_summary")).isEqualTo(8);
        for (UUID id : ids) {
            assertThat(count("select point_count from route_summary where workday_id = ?", id)).as("no double count").isEqualTo(20);
        }
    }

    @Test
    void aWorkdayAnotherRunnerHoldsIsSkippedAndDoneOnTheNextRun() throws Exception {
        UUID id = walk(100, 12);
        TransactionTemplate holder = new TransactionTemplate(transactionManager);
        ExecutorService otherRunner = Executors.newSingleThreadExecutor();

        RetentionRunResult whileHeld;
        try {
            whileHeld = holder.execute(status -> {
                jdbc.queryForObject("select id from workday where id = ? for update", UUID.class, id);
                try {
                    // Another thread, so another connection: its claim finds the row locked and skips it.
                    return otherRunner.submit(() -> runner.run(NOW)).get(60, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            });
        } finally {
            otherRunner.shutdownNow();
        }

        assertThat(whileHeld.workdaysSummarized()).isZero();
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isEqualTo(12);
        assertThat(runner.run(NOW).workdaysSummarized()).isEqualTo(1);
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isZero();
    }

    @Test
    void aRecentPointThatArrivedAfterTheCandidatesWereListedLeavesTheWorkdayWhole() {
        // The runner lists candidates first and claims each workday afterwards; a point recorded in the
        // meantime must stop that workday from being summarised (never only its old points).
        UUID id = walk(100, 10);
        point(id, NOW.minus(Duration.ofDays(1)), 99, -12.5, -77.0, 8.0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        Integer deleted = tx.execute(status -> gps.summarizeAndDelete(id, NOW.minus(Duration.ofDays(90)), NOW));

        assertThat(deleted).isZero();
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isEqualTo(11);
        assertThat(count("select count(*) from route_summary")).isZero();
    }

    @Test
    void theDatabaseRefusesPeriodsOutsideTheDecidedRanges() {
        for (int[] bad : new int[][] {{0, 12}, {-5, 12}, {29, 12}, {366, 12}, {90, 0}, {90, 2}, {90, 37}}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update(
                            "insert into retention_settings_copy (id, gps_days, photo_months, consent_version, last_occurred_at) values (1, ?, ?, 1, now())",
                            bad[0], bad[1]))
                    .as("gpsDays=%d photoMonths=%d", bad[0], bad[1])
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        }
        assertThat(count("select count(*) from retention_settings_copy")).isZero();
    }

    @Test
    void termsOutsideTheRangesFallBackToTheDefaultsInsteadOfDeletingRecentData() {
        assertThat(RetentionSettingsCopy.validOrDefaults(new RetentionSettingsCopy.RetentionTerms(0, 12)))
                .isEqualTo(RetentionSettingsCopy.DEFAULTS);
        assertThat(RetentionSettingsCopy.validOrDefaults(new RetentionSettingsCopy.RetentionTerms(90, 99)))
                .isEqualTo(RetentionSettingsCopy.DEFAULTS);
        assertThat(RetentionSettingsCopy.validOrDefaults(new RetentionSettingsCopy.RetentionTerms(30, 3)))
                .isEqualTo(new RetentionSettingsCopy.RetentionTerms(30, 3));
    }

    @Test
    void pointsThatArriveAfterASummaryAreAddedToIt() {
        UUID id = walk(100, 10);
        runner.run(NOW);
        Map<String, Object> first = jdbc.queryForMap("select distance_m, polyline from route_summary where workday_id = ?", id);
        Instant late = NOW.minus(Duration.ofDays(100)).plusSeconds(10_000);
        point(id, late, 50, -12.2, -77.0, 8.0);
        point(id, late.plusSeconds(20), 51, -12.2001, -77.0, 8.0);

        runner.run(NOW);

        assertThat(count("select point_count from route_summary where workday_id = ?", id)).isEqualTo(12);
        assertThat(count("select count(*) from workday_point where workday_id = ?", id)).isZero();
        assertThat(jdbc.queryForObject("select last_at from route_summary where workday_id = ?", Timestamp.class, id).toInstant())
                .isEqualTo(late.plusSeconds(20));
        // The documented limit: distance adds the new legs only, and the polyline stays the first one.
        Map<String, Object> merged = jdbc.queryForMap("select distance_m, polyline from route_summary where workday_id = ?", id);
        assertThat((Double) merged.get("distance_m")).isCloseTo((Double) first.get("distance_m") + 11.12, within(1.0));
        assertThat(merged.get("polyline")).isEqualTo(first.get("polyline"));
    }
}

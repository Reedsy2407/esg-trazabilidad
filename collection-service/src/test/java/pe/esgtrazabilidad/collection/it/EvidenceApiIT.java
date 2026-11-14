package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
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
import io.restassured.specification.RequestSpecification;
import pe.esgtrazabilidad.collection.evidence.EvidenceService;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.InMemoryEvidenceStorage;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/** CA-37: signed upload URL, key built by the server, and the check-in that names a photo. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
@Import(InMemoryEvidenceStorage.Config.class)
class EvidenceApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final String SHA256 = "a".repeat(64);
    private static final Instant NOON = Instant.parse("2026-11-14T17:00:00Z");

    @LocalServerPort
    private int port;

    @Autowired
    private RecyclerStatusCopyRepository copies;

    @Autowired
    private InMemoryEvidenceStorage bucket;

    @Autowired
    private EvidenceService evidence;

    @Autowired
    private JdbcTemplate jdbc;

    private String association;
    private String recycler;
    private String workday;
    private String neighbor;
    private String schedule;

    @BeforeEach
    void setUp() {
        bucket.reset();
        association = UUID.randomUUID().toString();
        recycler = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, recycler, association, RecyclerStatus.ACTIVE);
        RestAssuredSetup.authenticated(port);
        neighbor = FieldFixtures.neighbor("Vecino FOTO", "150101");
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

    private static String presignBody(String clientId, String contentType, long size, String sha) {
        return "{\"checkinClientId\": \"%s\", \"contentType\": \"%s\", \"sizeBytes\": %d, \"sha256\": \"%s\"}"
                .formatted(clientId, contentType, size, sha);
    }

    private String checkinBody(String clientId, String photoKey) {
        return """
                {"clientId": "%s", "neighborId": "%s", "scheduleId": "%s", "recordedAt": "%s", "weightKg": 2.5,
                 "wasteType": "PET", "deviceSeq": 1, "photoKey": "%s"}
                """.formatted(clientId, neighbor, schedule, NOON, photoKey);
    }

    private String presign(String clientId, long size) {
        return as(recycler).body(presignBody(clientId, "image/jpeg", size, SHA256)).post("/evidence/presign")
                .then().statusCode(200).extract().path("key");
    }

    @Test
    void presignReturnsAUrlThatExpiresInTenMinutesAndAKeyBuiltByTheServer() {
        String clientId = UUID.randomUUID().toString();
        int year = Instant.now().atZone(ZoneId.of("America/Lima")).getYear();

        as(recycler).body(presignBody(clientId, "image/jpeg", 150_000, SHA256.toUpperCase())).post("/evidence/presign")
                .then().statusCode(200)
                .body("key", equalTo("evidence/" + association + "/" + year + "/" + clientId + ".jpg"))
                .body("expiresIn", equalTo(600))
                .body("uploadUrl", startsWith("https://bucket.invalid/evidence/" + association));

        assertThat(bucket.lastUploadValidity()).isEqualTo(Duration.ofMinutes(10));
        assertThat(bucket.lastUploadSize()).as("the size is part of what is signed").isEqualTo(150_000L);
        assertThat(bucket.lastUploadSha256()).as("the hash is part of what is signed, normalised").isEqualTo(SHA256);
    }

    @Test
    void askingAgainForTheSameCheckinKeepsTheKeyAndRefreshesTheAgreement() {
        String clientId = UUID.randomUUID().toString();
        String first = presign(clientId, 100_000);
        String second = presign(clientId, 120_000);

        assertThat(second).isEqualTo(first);
        assertThat(jdbc.queryForObject("select size_bytes from evidence_presign where checkin_client_id = ?::uuid", Long.class, clientId))
                .isEqualTo(120_000L);
        assertThat(jdbc.queryForObject("select count(*) from evidence_presign where checkin_client_id = ?::uuid", Long.class, clientId))
                .isEqualTo(1L);
    }

    @Test
    void onlyJpegsWithinTheLimitAndAValidHashAreAccepted() {
        String clientId = UUID.randomUUID().toString();
        for (String bad : new String[] {
                presignBody(clientId, "image/png", 1000, SHA256),
                presignBody(clientId, "image/jpeg", 2_097_153, SHA256),
                presignBody(clientId, "image/jpeg", 0, SHA256),
                presignBody(clientId, "image/jpeg", 1000, "abc"),
                presignBody(clientId, "image/jpeg", 1000, "z".repeat(64))}) {
            as(recycler).body(bad).post("/evidence/presign").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        }
        as(recycler).body(presignBody(clientId, "image/jpeg", 2_097_152, SHA256)).post("/evidence/presign").then().statusCode(200);
        assertThat(jdbc.queryForObject("select count(*) from evidence_presign where checkin_client_id = ?::uuid", Long.class, clientId))
                .isEqualTo(1L);
    }

    @Test
    void onlyAnActiveRecyclerWithConsentCanAskForAUrl() {
        String clientId = UUID.randomUUID().toString();
        as(UUID.randomUUID().toString()).body(presignBody(clientId, "image/jpeg", 1000, SHA256)).post("/evidence/presign")
                .then().statusCode(403).body("code", equalTo("COL-019"));
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recycler, association, null, false))
                .contentType(ContentType.JSON).body(presignBody(clientId, "image/jpeg", 1000, SHA256)).post("/evidence/presign")
                .then().statusCode(409).body("code", equalTo("AUTH-019"));
        RestAssuredSetup.authenticated(port);
        given().contentType(ContentType.JSON).body(presignBody(clientId, "image/jpeg", 1000, SHA256)).post("/evidence/presign")
                .then().statusCode(403).body("code", equalTo("AUTH-000"));
        assertThat(jdbc.queryForObject("select count(*) from evidence_presign where checkin_client_id = ?::uuid", Long.class, clientId))
                .isZero();
    }

    @Test
    void aCheckinWithAPhotoIsAcceptedOnlyIfTheBucketHoldsItWithTheAgreedSize() {
        String clientId = UUID.randomUUID().toString();
        String key = presign(clientId, 150_000);

        // Not uploaded yet: retryable conflict.
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-018"));
        // Uploaded, but not what was agreed.
        bucket.put(key, 149_999);
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-018"));
        assertThat(jdbc.queryForObject("select count(*) from collection_record where recycler_id = ?::uuid", Long.class, recycler)).isZero();

        // The upload is redone: the same check-in now goes through, and keeps its photo key.
        bucket.put(key, 150_000);
        String checkinId = as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(201).extract().path("checkinId");
        assertThat(jdbc.queryForObject("select photo_key from field_checkin where id = ?::uuid", String.class, checkinId)).isEqualTo(key);
    }

    @Test
    void aKeyThatWasNeverAgreedForThisRecyclerIsRefused() {
        String clientId = UUID.randomUUID().toString();
        // Nothing presigned for this check-in: even an object that exists under the expected path is refused.
        String forged = "evidence/" + association + "/2026/" + clientId + ".jpg";
        bucket.put(forged, 1000);
        as(recycler).body(checkinBody(clientId, forged)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-018"));

        // Another recycler cannot use a key agreed for the first one, even for the same client id.
        String key = presign(clientId, 1000);
        bucket.put(key, 1000);
        String other = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, other, association, RecyclerStatus.ACTIVE);
        String otherWorkday = as(other).body("{\"startedAt\": \"2026-11-14T13:00:00Z\", \"clientId\": \"%s\"}".formatted(UUID.randomUUID()))
                .post("/workdays").then().statusCode(201).extract().path("workdayId");
        as(other).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", otherWorkday)
                .then().statusCode(409).body("code", equalTo("COL-018"));
        // A different key than the agreed one is refused for the owner too.
        as(recycler).body(checkinBody(clientId, key.replace(".jpg", "-x.jpg"))).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-018"));
    }

    @Test
    void aBucketThatCannotBeReachedIsARetryableConflictAndTheCallNeverRunsInsideATransaction() {
        String clientId = UUID.randomUUID().toString();
        String key = presign(clientId, 1000);
        bucket.put(key, 1000);

        bucket.failNextHeads(1);
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday)
                .then().statusCode(409).body("code", equalTo("COL-018"));
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday).then().statusCode(201);

        assertThat(bucket.callsInsideTransaction()).as("the bucket was called twice, never inside a transaction")
                .hasSize(2).containsOnly(false);
    }

    @Test
    void aRepeatedCheckinDoesNotCallTheBucketAgain() {
        String clientId = UUID.randomUUID().toString();
        String key = presign(clientId, 1000);
        bucket.put(key, 1000);
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday).then().statusCode(201);
        int calls = bucket.callsInsideTransaction().size();

        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday).then().statusCode(200);

        assertThat(bucket.callsInsideTransaction()).hasSize(calls);
    }

    @Test
    void aCheckinAlreadyAcceptedCannotGetANewUploadUrlForItsPhoto() {
        String clientId = UUID.randomUUID().toString();
        String key = presign(clientId, 1000);
        bucket.put(key, 1000);
        as(recycler).body(checkinBody(clientId, key)).post("/workdays/{id}/checkins", workday).then().statusCode(201);

        as(recycler).body(presignBody(clientId, "image/jpeg", 2000, SHA256)).post("/evidence/presign")
                .then().statusCode(409).body("code", equalTo("COL-020"));
        assertThat(jdbc.queryForObject("select size_bytes from evidence_presign where checkin_client_id = ?::uuid", Long.class, clientId))
                .as("the agreement of the accepted check-in was not touched").isEqualTo(1000L);
    }

    @Test
    void twoRecyclersOfTheSameAssociationCannotShareACheckinClientIdKey() {
        String clientId = UUID.randomUUID().toString();
        presign(clientId, 1000);
        String other = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, other, association, RecyclerStatus.ACTIVE);

        as(other).body(presignBody(clientId, "image/jpeg", 1000, SHA256)).post("/evidence/presign")
                .then().statusCode(409).body("code", equalTo("COL-020"));
    }

    @Test
    void theReadUrlIsSignedAndShortLived() {
        String url = evidence.readUrl("evidence/x/2026/y.jpg");

        assertThat(url).contains("signed-read");
        assertThat(bucket.lastReadValidity()).isEqualTo(Duration.ofMinutes(5));
    }
}

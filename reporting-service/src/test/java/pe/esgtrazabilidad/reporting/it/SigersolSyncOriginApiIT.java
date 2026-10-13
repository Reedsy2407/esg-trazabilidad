package pe.esgtrazabilidad.reporting.it;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

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

import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;
import pe.esgtrazabilidad.reporting.it.support.TestRucs;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

/**
 * D1: every SIGERSOL record says how the figure was obtained and who registered it (from the token).
 * Records made before v3 have neither and read "origen no registrado"; a company's period is
 * RECORDED or UNVERIFIED ("No verificado").
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class SigersolSyncOriginApiIT {

    private static final AtomicLong RUC_SEQUENCE = new AtomicLong(73_000_000L);
    private static final String ACTOR_ID = "01a0d8cb-f219-72a2-b006-5150f0e0d333";

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
    void setUp() {
        RestAssuredSetup.authenticated(port);
    }

    private static String body(UUID associationId, String origin, String reference) {
        return """
                { "associationId": "%s", "periodStart": "2026-03-01", "periodEnd": "2026-03-31",
                  "hierarchyCompliancePercent": 70.5 %s %s }
                """.formatted(
                associationId,
                origin == null ? "" : ", \"origin\": \"" + origin + "\"",
                reference == null ? "" : ", \"reference\": \"" + reference + "\"");
    }

    @Test
    void theOriginTheReferenceAndTheAuthorFromTheTokenAreStoredAndReturned() {
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.validTokenFor(ACTOR_ID, "ana@example.com"))
                .contentType(ContentType.JSON)
                .body("""
                        { "associationId": "%s", "periodStart": "2026-03-01", "periodEnd": "2026-03-31",
                          "hierarchyCompliancePercent": 70.5, "origin": "REFERENCED_PROOF",
                          "reference": "  constancia-marzo.png ", "registeredById": "ignored",
                          "registeredByEmail": "otro@example.com" }
                        """.formatted(UUID.randomUUID()))
                .when().post("/sigersol-syncs")
                .then().statusCode(201)
                .body("origin", equalTo("REFERENCED_PROOF"))
                .body("reference", equalTo("constancia-marzo.png"))
                .body("registeredById", equalTo(ACTOR_ID))
                .body("registeredByEmail", equalTo("ana@example.com"));
    }

    @Test
    void theOriginIsRequiredAndMustBeOneOfTheTwoValues() {
        given().contentType(ContentType.JSON).body(body(UUID.randomUUID(), null, null))
                .when().post("/sigersol-syncs").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().contentType(ContentType.JSON).body(body(UUID.randomUUID(), "VERIFIED_BY_SIGERSOL", null))
                .when().post("/sigersol-syncs").then().statusCode(400);
        given().contentType(ContentType.JSON).body(body(UUID.randomUUID(), "DECLARED_BY_OPERATOR", "x".repeat(256)))
                .when().post("/sigersol-syncs").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void aRecordMadeBeforeV3ReadsWithoutOriginAndAuthor() {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO sigersol_sync (id, association_id, period_start, period_end, "
                + "hierarchy_compliance_percent, declared_at) VALUES (?, ?, '2025-01-01', '2025-12-31', 60, now())",
                id, UUID.randomUUID());

        given().when().get("/sigersol-syncs/{id}", id.toString())
                .then().statusCode(200)
                .body("origin", nullValue())
                .body("registeredById", nullValue())
                .body("registeredByEmail", nullValue())
                .body("reference", nullValue());
    }

    @Test
    void originAndAuthorCannotDriftApartInTheDatabase() {
        UUID id = UUID.randomUUID();
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("INSERT INTO sigersol_sync (id, association_id, period_start, period_end, "
                        + "hierarchy_compliance_percent, declared_at, origin) "
                        + "VALUES (?, ?, '2025-01-01', '2025-12-31', 60, now(), 'DECLARED_BY_OPERATOR')",
                        id, UUID.randomUUID()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sigersol_sync WHERE id = ?", Integer.class, id)).isZero();
    }

    @Test
    void aCompanysPeriodIsRecordedWithItsOriginOrUnverifiedWhenNoRecordCoversIt() {
        UUID associationId = UUID.randomUUID();
        String companyId = given().contentType(ContentType.JSON)
                .body("{ \"ruc\": \"%s\", \"associationId\": \"%s\" }"
                        .formatted(TestRucs.fromSequence(RUC_SEQUENCE.incrementAndGet()), associationId))
                .when().post("/tracked-companies").then().statusCode(201).extract().path("id");

        given().queryParam("periodStart", "2026-03-01").queryParam("periodEnd", "2026-03-31")
                .when().get("/tracked-companies/{id}/certificate-summary", companyId)
                .then().statusCode(200)
                .body("sigersolCoverage", equalTo("UNVERIFIED"))
                .body("hierarchyCompliancePercent", nullValue())
                .body("sigersolOrigin", nullValue());

        given().contentType(ContentType.JSON).body(body(associationId, "DECLARED_BY_OPERATOR", null))
                .when().post("/sigersol-syncs").then().statusCode(201);

        given().queryParam("periodStart", "2026-03-01").queryParam("periodEnd", "2026-03-31")
                .when().get("/tracked-companies/{id}/certificate-summary", companyId)
                .then().statusCode(200)
                .body("sigersolCoverage", equalTo("RECORDED"))
                .body("sigersolOrigin", equalTo("DECLARED_BY_OPERATOR"))
                .body("sigersolRegisteredByEmail", equalTo("staff@example.com"))
                .body("hierarchyCompliancePercent", equalTo(70.5f));
    }

    @Test
    void aPeriodCoveredByARecordMadeBeforeV3IsRecordedWithoutAnOrigin() {
        UUID associationId = UUID.randomUUID();
        jdbc.update("INSERT INTO sigersol_sync (id, association_id, period_start, period_end, "
                + "hierarchy_compliance_percent, declared_at) VALUES (?, ?, '2025-01-01', '2025-12-31', 60, now())",
                UUID.randomUUID(), associationId);
        String companyId = given().contentType(ContentType.JSON)
                .body("{ \"ruc\": \"%s\", \"associationId\": \"%s\" }"
                        .formatted(TestRucs.fromSequence(RUC_SEQUENCE.incrementAndGet()), associationId))
                .when().post("/tracked-companies").then().statusCode(201).extract().path("id");

        given().queryParam("periodStart", "2025-03-01").queryParam("periodEnd", "2025-03-31")
                .when().get("/tracked-companies/{id}/certificate-summary", companyId)
                .then().statusCode(200)
                .body("sigersolCoverage", equalTo("RECORDED"))
                .body("sigersolOrigin", nullValue())
                .body("sigersolRegisteredByEmail", nullValue())
                .body("hierarchyCompliancePercent", equalTo(60.0f));
    }
}

package pe.esgtrazabilidad.reporting.it;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CertificateApiIT {

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
    private TracedCollectionEntryJpaRepository tracedCollectionEntryRepository;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    private String registerTrackedCompanyRequest(String ruc, UUID associationId) {
        return """
                {
                  "name": "Empresa Certificado IT",
                  "ruc": "%s",
                  "associationId": "%s"
                }
                """.formatted(ruc, associationId);
    }

    private String registerSigersolSyncRequest(UUID associationId, String periodStart, String periodEnd) {
        return """
                {
                  "associationId": "%s",
                  "periodStart": "%s",
                  "periodEnd": "%s",
                  "hierarchyCompliancePercent": 88.5
                }
                """.formatted(associationId, periodStart, periodEnd);
    }

    private String issueCertificateRequest(String periodStart, String periodEnd) {
        return """
                {
                  "periodStart": "%s",
                  "periodEnd": "%s"
                }
                """.formatted(periodStart, periodEnd);
    }

    private String registerTrackedCompany(String ruc, UUID associationId) {
        return given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest(ruc, associationId))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void registerSigersolSync(UUID associationId, String periodStart, String periodEnd) {
        given()
                .contentType(ContentType.JSON)
                .body(registerSigersolSyncRequest(associationId, periodStart, periodEnd))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);
    }

    /**
     * Inserts a TracedCollectionEntry directly, bypassing the real
     * RabbitMQ event flow -- Task 49 already proved that path end to end;
     * this test only needs a real row to sum, not to re-prove consumption.
     */
    private void seedTracedCollection(UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
        tracedCollectionEntryRepository.save(
                new TracedCollectionEntryEntity(UUID.randomUUID(), associationId, collectionDate, weightKg, Instant.now()));
    }

    @Test
    void previewingASummaryWithNoTracedKilosOrSigersolDataReturnsZeroKilosAndNullCompliance() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20444444444", associationId);

        given()
                .queryParam("periodStart", "2026-01-01")
                .queryParam("periodEnd", "2026-01-31")
                .when()
                .get("/tracked-companies/{companyId}/certificate-summary", companyId)
                .then()
                .statusCode(200)
                .body("kilosTrazados", equalTo(0))
                .body("hierarchyCompliancePercent", nullValue());
    }

    @Test
    void previewingASummaryAfterRegisteringSigersolDataIncludesTheCompliancePercent() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20555555555", associationId);
        registerSigersolSync(associationId, "2026-02-01", "2026-02-28");

        given()
                .queryParam("periodStart", "2026-02-01")
                .queryParam("periodEnd", "2026-02-28")
                .when()
                .get("/tracked-companies/{companyId}/certificate-summary", companyId)
                .then()
                .statusCode(200)
                .body("hierarchyCompliancePercent", equalTo(88.5f));
    }

    @Test
    void previewingForAMissingTrackedCompanyReturnsNotFound() {
        given()
                .queryParam("periodStart", "2026-01-01")
                .queryParam("periodEnd", "2026-01-31")
                .when()
                .get("/tracked-companies/{companyId}/certificate-summary", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-001"));
    }

    @Test
    void issuingACertificateFreezesTheComputedKilosAndCompliancePercent() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20666666666", associationId);
        registerSigersolSync(associationId, "2026-03-01", "2026-03-31");
        seedTracedCollection(associationId, LocalDate.of(2026, 3, 10), new BigDecimal("10.00"));
        seedTracedCollection(associationId, LocalDate.of(2026, 3, 20), new BigDecimal("4.50"));

        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-03-01", "2026-03-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201)
                .body("trackedCompanyId", equalTo(companyId))
                .body("associationId", equalTo(associationId.toString()))
                .body("kilosTrazados", equalTo(14.5f))
                .body("hierarchyCompliancePercent", equalTo(88.5f))
                .body("id", org.hamcrest.Matchers.notNullValue());
    }

    @Test
    void issuingWithAnOverlappingPeriodReturnsConflict() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20777777777", associationId);
        registerSigersolSync(associationId, "2026-04-01", "2026-05-31");
        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-04-01", "2026-04-30"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-04-15", "2026-05-15"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-004"));
    }

    @Test
    void issuingANonOverlappingAdjacentPeriodForTheSameTrackedCompanySucceeds() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20777777778", associationId);
        registerSigersolSync(associationId, "2026-04-01", "2026-05-31");
        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-04-01", "2026-04-30"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-05-01", "2026-05-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201);
    }

    @Test
    void issuingWithoutCoveringSigersolDataReturnsConflict() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20888888888", associationId);

        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-06-01", "2026-06-30"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-005"));
    }

    @Test
    void issuingForAMissingTrackedCompanyReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-01-01", "2026-01-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-001"));
    }

    @Test
    void gettingAndListingAnIssuedCertificateReturnsIt() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20999999999", associationId);
        registerSigersolSync(associationId, "2026-07-01", "2026-07-31");
        String certificateId = given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-07-01", "2026-07-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", companyId, certificateId)
                .then()
                .statusCode(200)
                .body("id", equalTo(certificateId));

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(200)
                .body("content.size()", equalTo(1))
                .body("content[0].id", equalTo(certificateId));
    }

    @Test
    void gettingACertificateThroughADifferentCompanysPathReturnsNotFound() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20100000001", associationId);
        registerSigersolSync(associationId, "2026-08-01", "2026-08-31");
        String certificateId = given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-08-01", "2026-08-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
        String otherCompanyId = registerTrackedCompany("20100000002", UUID.randomUUID());

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", otherCompanyId, certificateId)
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-003"));
    }
}

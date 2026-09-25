package pe.esgtrazabilidad.reporting.it;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import io.restassured.http.ContentType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;

import org.springframework.beans.factory.annotation.Autowired;

import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

/**
 * Same round-trip standard as Tasks 54/55's own unit tests
 * (CertificatePdfExporterTest / CertificateCsvExporterTest), now proven over
 * real HTTP: download the actual response bytes and parse them back with
 * PDFBox's/Commons CSV's own reader, not just check the content type header.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class CertificateExportApiIT {

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
        RestAssuredSetup.authenticated(port);
    }

    private String registerTrackedCompanyRequest(String ruc, UUID associationId) {
        return """
                {
                  "name": "Empresa Export IT",
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
                  "hierarchyCompliancePercent": 92.0
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

    private void seedTracedCollection(UUID associationId, LocalDate collectionDate, BigDecimal weightKg) {
        tracedCollectionEntryRepository.save(
                new TracedCollectionEntryEntity(UUID.randomUUID(), associationId, collectionDate, weightKg, Instant.now()));
    }

    private String issueCertificate(String companyId, String periodStart, String periodEnd) {
        return given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest(periodStart, periodEnd))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void downloadingThePdfReturnsAValidPdfWithTheCertificatesData() throws IOException {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20200000001", associationId);
        registerSigersolSync(associationId, "2026-10-01", "2026-10-31");
        seedTracedCollection(associationId, LocalDate.of(2026, 10, 5), new BigDecimal("42.00"));
        String certificateId = issueCertificate(companyId, "2026-10-01", "2026-10-31");

        byte[] pdfBytes = given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}/pdf", companyId, certificateId)
                .then()
                .statusCode(200)
                .contentType("application/pdf")
                .header("Content-Disposition", startsWith("attachment"))
                .extract()
                .asByteArray();

        String extractedText;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            extractedText = new PDFTextStripper().getText(document);
        }

        assertThat(extractedText).contains("Empresa Export IT");
        assertThat(extractedText).contains("20200000001");
        assertThat(extractedText).contains("2026-10-01");
        assertThat(extractedText).contains("2026-10-31");
        assertThat(extractedText).contains("42.00");
        assertThat(extractedText).contains("92.00");
    }

    @Test
    void downloadingTheCsvReturnsAValidCsvWithTheSummaryAndLineItems() throws IOException {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20200000002", associationId);
        registerSigersolSync(associationId, "2026-11-01", "2026-11-30");
        seedTracedCollection(associationId, LocalDate.of(2026, 11, 5), new BigDecimal("15.00"));
        seedTracedCollection(associationId, LocalDate.of(2026, 11, 20), new BigDecimal("25.00"));
        String certificateId = issueCertificate(companyId, "2026-11-01", "2026-11-30");

        byte[] csvBytes = given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}/csv", companyId, certificateId)
                .then()
                .statusCode(200)
                .contentType("text/csv")
                .header("Content-Disposition", startsWith("attachment"))
                .extract()
                .asByteArray();

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(new String(csvBytes, StandardCharsets.UTF_8), CSVFormat.DEFAULT)) {
            records = parser.getRecords();
        }

        assertThat(records.get(0)).containsExactly("Empresa", "Empresa Export IT");
        assertThat(records.get(4)).containsExactly("Kilos trazados", "40.00");
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("2026-11-05", "15.00"));
        assertThat(records).anySatisfy(r -> assertThat(r).containsExactly("2026-11-20", "25.00"));
    }

    @Test
    void downloadingAnExportThroughADifferentCompanysPathReturnsNotFound() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20200000003", associationId);
        registerSigersolSync(associationId, "2026-12-01", "2026-12-31");
        String certificateId = issueCertificate(companyId, "2026-12-01", "2026-12-31");
        String otherCompanyId = registerTrackedCompany("20200000004", UUID.randomUUID());

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}/pdf", otherCompanyId, certificateId)
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-003"));

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}/csv", otherCompanyId, certificateId)
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-003"));
    }
}

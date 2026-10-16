package pe.esgtrazabilidad.reporting.it;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import io.restassured.http.ContentType;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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

import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryEntity;
import pe.esgtrazabilidad.reporting.events.ledger.TracedCollectionEntryJpaRepository;
import pe.esgtrazabilidad.reporting.it.support.FakeSigersolMunicipal;
import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;
import pe.esgtrazabilidad.reporting.it.support.TestRucs;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats.Answer;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/**
 * M5: issuing freezes, for each district of the certificate, what SIGERSOL Municipal says (or that
 * it is "no disponible"); exporting never asks SIGERSOL again; nothing blocks on SIGERSOL.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class CertificateDistrictContextApiIT {

    private static final AtomicLong RUC_SEQUENCE = new AtomicLong(74_000_000L);
    private static final int LAST_YEAR = Instant.now().atZone(java.time.ZoneOffset.UTC).getYear() - 1;

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
    private TracedCollectionEntryJpaRepository ledger;

    @Autowired
    private FakeSigersolMunicipal sigersol;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssuredSetup.authenticated(port);
        sigersol.reset();
    }

    private record Setup(UUID associationId, String companyId) {
    }

    private Setup companyWithSigersolRecord() {
        UUID associationId = UUID.randomUUID();
        String companyId = given().contentType(ContentType.JSON)
                .body("{ \"ruc\": \"%s\", \"associationId\": \"%s\" }"
                        .formatted(TestRucs.fromSequence(RUC_SEQUENCE.incrementAndGet()), associationId))
                .when().post("/tracked-companies").then().statusCode(201).extract().path("id");
        given().contentType(ContentType.JSON)
                .body("""
                        { "associationId": "%s", "periodStart": "2026-01-01", "periodEnd": "2026-12-31",
                          "origin": "DECLARED_BY_OPERATOR", "hierarchyCompliancePercent": 80.0 }
                        """.formatted(associationId))
                .when().post("/sigersol-syncs").then().statusCode(201);
        return new Setup(associationId, companyId);
    }

    private void collect(UUID associationId, String kilos, String districtUbigeo) {
        ledger.save(new TracedCollectionEntryEntity(
                UUID.randomUUID(), associationId, LocalDate.of(2026, 3, 5), new BigDecimal(kilos), Instant.now(), districtUbigeo));
    }

    private String issue(String companyId) {
        return given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-03-01\", \"periodEnd\": \"2026-03-31\" }")
                .when().post("/tracked-companies/{id}/certificates", companyId)
                .then().statusCode(201).extract().path("id");
    }

    @Test
    void theContextOfEachDistrictIsFrozenAtIssueAndReExportingNeverAsksSigersolAgain() throws IOException {
        Setup s = companyWithSigersolRecord();
        sigersol.answer(LAST_YEAR, "150114", new Answer.Found(3, 31));
        collect(s.associationId(), "40.00", "150114");
        collect(s.associationId(), "2.00", "150114");
        collect(s.associationId(), "5.50", null);

        String certificateId = issue(s.companyId());

        given().when().get("/tracked-companies/{c}/certificates/{id}", s.companyId(), certificateId)
                .then().statusCode(200)
                .body("districtContext", hasSize(2))
                .body("districtContext[0].districtName", equalTo("La Molina"))
                .body("districtContext[0].status", equalTo("AVAILABLE"))
                .body("districtContext[0].year", equalTo(LAST_YEAR))
                .body("districtContext[0].associationCount", equalTo(3))
                .body("districtContext[0].recyclerCount", equalTo(31))
                .body("districtContext[0].kilos", equalTo(42.0f))
                .body("districtContext[1].status", equalTo("UNKNOWN_DISTRICT"))
                .body("districtContext[1].districtUbigeo", nullValue())
                .body("districtContext[1].kilos", equalTo(5.5f));

        // SIGERSOL changes its mind or disappears: the certificate keeps what it froze.
        int callsAtIssue = sigersol.calls();
        sigersol.answer(LAST_YEAR, "150114", new Answer.Found(99, 999));
        sigersol.unavailableAll(true);
        jdbc.update("DELETE FROM sigersol_district_snapshot");

        byte[] pdf = given().when()
                .get("/tracked-companies/{c}/certificates/{id}/pdf", s.companyId(), certificateId)
                .then().statusCode(200).extract().asByteArray();
        String csv = new String(
                given().when().get("/tracked-companies/{c}/certificates/{id}/csv", s.companyId(), certificateId)
                        .then().statusCode(200).extract().asByteArray(),
                StandardCharsets.UTF_8);

        assertThat(sigersol.calls()).as("exports never ask SIGERSOL").isEqualTo(callsAtIssue);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("La Molina").contains("42.00 kg");
            assertThat(text).contains("Asociaciones: 3; recicladores: 31");
            assertThat(text).contains("Fuente: SIGERSOL Municipal, año " + LAST_YEAR + ", datos agregados");
            assertThat(text).contains("Distrito desconocido").contains("5.50 kg");
            assertThat(text).doesNotContain("Asociaciones: 99").doesNotContain("recicladores: 999");
        }
        // The source holds commas, so it is a quoted field.
        assertThat(csv).contains("La Molina,150114,42.00,Disponible,3,31,\"SIGERSOL Municipal, año " + LAST_YEAR
                + ", datos agregados\"");
    }

    @Test
    void anOutageNeverBlocksTheCertificateAndItReadsNoDisponible() throws IOException {
        Setup s = companyWithSigersolRecord();
        sigersol.unavailableAll(true);
        collect(s.associationId(), "10.00", "150131");

        String certificateId = issue(s.companyId());

        given().when().get("/tracked-companies/{c}/certificates/{id}", s.companyId(), certificateId)
                .then().statusCode(200)
                .body("districtContext", hasSize(1))
                .body("districtContext[0].districtName", equalTo("San Isidro"))
                .body("districtContext[0].status", equalTo("UNAVAILABLE"))
                .body("districtContext[0].year", nullValue());
        byte[] pdf = given().when()
                .get("/tracked-companies/{c}/certificates/{id}/pdf", s.companyId(), certificateId)
                .then().statusCode(200).extract().asByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(new PDFTextStripper().getText(document).replaceAll("\\s+", " ")).contains("Contexto de SIGERSOL Municipal no disponible");
        }
    }

    @Test
    void aValueTheSnapshotTableRefusesNeverFailsTheIssueOfTheCertificate() {
        Setup s = companyWithSigersolRecord();
        // Negative counts violate ck_sigersol_district_snapshot: the save fails inside the issue.
        sigersol.answer(LAST_YEAR, "150132", new Answer.Found(-1, 1));
        collect(s.associationId(), "6.00", "150132");

        String certificateId = issue(s.companyId());

        given().when().get("/tracked-companies/{c}/certificates/{id}", s.companyId(), certificateId)
                .then().statusCode(200)
                .body("districtContext[0].status", equalTo("UNAVAILABLE"))
                .body("kilosTrazados", equalTo(6.0f));
    }

    @Test
    void sigersolIsNeverCalledInsideTheIssueTransaction() {
        Setup s = companyWithSigersolRecord();
        sigersol.answer(LAST_YEAR, "150133", new Answer.Found(1, 2));
        collect(s.associationId(), "4.00", "150133");

        issue(s.companyId());

        assertThat(sigersol.calls()).isPositive();
        assertThat(sigersol.callsInsideATransaction()).isZero();
    }

    @Test
    void aLedgerWithoutDistrictsIssuesWithOnlyTheUnknownDistrictLine() {
        Setup s = companyWithSigersolRecord();
        collect(s.associationId(), "7.00", null);

        String certificateId = issue(s.companyId());

        given().when().get("/tracked-companies/{c}/certificates/{id}", s.companyId(), certificateId)
                .then().statusCode(200)
                .body("districtContext", hasSize(1))
                .body("districtContext[0].status", equalTo("UNKNOWN_DISTRICT"));
        assertThat(sigersol.calls()).isZero();
    }

    @Test
    void thePreviewShowsTheLiveDistrictsAndAListPageLeavesTheContextOut() {
        Setup s = companyWithSigersolRecord();
        sigersol.answer(LAST_YEAR, "150131", new Answer.Found(5, 62));
        collect(s.associationId(), "9.00", "150131");

        given().queryParam("periodStart", "2026-03-01").queryParam("periodEnd", "2026-03-31")
                .when().get("/tracked-companies/{id}/certificate-summary", s.companyId())
                .then().statusCode(200)
                .body("districts", hasSize(1))
                .body("districts[0].districtName", equalTo("San Isidro"))
                .body("districts[0].associationCount", equalTo(5));

        issue(s.companyId());
        given().when().get("/tracked-companies/{id}/certificates", s.companyId())
                .then().statusCode(200).body("content[0].districtContext", nullValue());
    }

    @Test
    void theIssuedCertificateAndItsLineItemsCarryTheLedgersDistrict() {
        Setup s = companyWithSigersolRecord();
        collect(s.associationId(), "3.00", "150114");

        String certificateId = issue(s.companyId());

        assertThat(jdbc.queryForObject(
                        "SELECT district_ubigeo FROM esg_certificate_line_item WHERE certificate_id = ?::uuid",
                        String.class, certificateId))
                .isEqualTo("150114");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM esg_certificate_district_context WHERE certificate_id = ?::uuid",
                        Integer.class, certificateId))
                .isEqualTo(1);
    }
}

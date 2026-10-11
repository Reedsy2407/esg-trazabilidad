package pe.esgtrazabilidad.reporting.it;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

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

import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * v3 / M3: annulling a certificate (never deleting it), the actor coming from
 * the token, the period it frees, the PDF/CSV marks, the INACTIVE-company
 * block, and two simultaneous annulments of the same certificate.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class CertificateAnnulmentApiIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final AtomicLong RUC_SEQUENCE = new AtomicLong(20_310_000_000L);

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
    }

    private String registerCompany(UUID associationId) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        { "name": "Empresa Anulación IT", "ruc": "%d", "associationId": "%s" }
                        """.formatted(RUC_SEQUENCE.incrementAndGet(), associationId))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private void registerSigersol(UUID associationId) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "associationId": "%s", "periodStart": "2026-01-01", "periodEnd": "2026-12-31",
                          "hierarchyCompliancePercent": 80.0 }
                        """.formatted(associationId))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);
    }

    private Response issue(String companyId, String periodStart, String periodEnd) {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        { "periodStart": "%s", "periodEnd": "%s" }
                        """.formatted(periodStart, periodEnd))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId);
    }

    private String issuedCertificateId(String companyId, String periodStart, String periodEnd) {
        return issue(companyId, periodStart, periodEnd).then().statusCode(201).extract().path("id");
    }

    private Response annul(String companyId, String certificateId, String reason) {
        return given()
                .contentType(ContentType.JSON)
                .body("{ \"reason\": \"%s\" }".formatted(reason))
                .when()
                .post("/tracked-companies/{companyId}/certificates/{id}/annul", companyId, certificateId);
    }

    /** A company with a SIGERSOL record for 2026 and one ISSUED certificate for January. */
    private String[] companyWithJanuaryCertificate() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerCompany(associationId);
        registerSigersol(associationId);
        return new String[] {companyId, issuedCertificateId(companyId, "2026-01-01", "2026-01-31")};
    }

    @Test
    void aNewCertificateIsIssuedWithNoAnnulmentData() {
        String[] ids = companyWithJanuaryCertificate();

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", ids[0], ids[1])
                .then()
                .statusCode(200)
                .body("status", equalTo("ISSUED"))
                .body("annulledAt", nullValue())
                .body("annulledById", nullValue())
                .body("annulledByEmail", nullValue())
                .body("annulmentReason", nullValue());
    }

    @Test
    void annullingTakesTheActorFromTheTokenNeverFromTheBodyAndKeepsTheFrozenNumbers() {
        String[] ids = companyWithJanuaryCertificate();
        String actorId = "01a0d8cb-f219-72a2-b006-5150f0e0d111";
        String token = TestJwtTokens.validTokenFor(actorId, "ana@example.com");
        // The default spec carries its own token and would win over this one.
        RestAssuredSetup.anonymous(port);

        given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body("""
                        { "reason": "  Se emitió con datos erróneos ",
                          "annulledById": "00000000-0000-0000-0000-000000000000",
                          "annulledByEmail": "intruso@example.com",
                          "annulledAt": "2000-01-01T00:00:00Z", "status": "ISSUED" }
                        """)
                .when()
                .post("/tracked-companies/{companyId}/certificates/{id}/annul", ids[0], ids[1])
                .then()
                .statusCode(200)
                .body("status", equalTo("ANNULLED"))
                .body("annulmentReason", equalTo("Se emitió con datos erróneos"))
                .body("annulledById", equalTo(actorId))
                .body("annulledByEmail", equalTo("ana@example.com"))
                .body("annulledAt", notNullValue())
                .body("kilosTrazados", equalTo(0.0f))
                .body("hierarchyCompliancePercent", equalTo(80.0f))
                .body("periodStart", equalTo("2026-01-01"));

        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", ids[0], ids[1])
                .then()
                .statusCode(200)
                .body("status", equalTo("ANNULLED"))
                .body("annulledById", equalTo(actorId));
        given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/tracked-companies/{companyId}/certificates", ids[0])
                .then()
                .statusCode(200)
                .body("content[0].status", equalTo("ANNULLED"));
    }

    @Test
    void theReasonIsRequiredAndLimitedTo500Characters() {
        String[] ids = companyWithJanuaryCertificate();

        for (String body : List.of("{}", "{ \"reason\": \"\" }", "{ \"reason\": \"   \" }",
                "{ \"reason\": \"" + "x".repeat(501) + "\" }")) {
            given().contentType(ContentType.JSON).body(body)
                    .when()
                    .post("/tracked-companies/{companyId}/certificates/{id}/annul", ids[0], ids[1])
                    .then()
                    .statusCode(400);
        }
        assertThat(annul(ids[0], ids[1], "x".repeat(500)).statusCode()).isEqualTo(200);
    }

    @Test
    void annullingTwiceIsRpt019AndKeepsTheFirstReason() {
        String[] ids = companyWithJanuaryCertificate();
        annul(ids[0], ids[1], "Primer motivo").then().statusCode(200);

        annul(ids[0], ids[1], "Segundo motivo")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-019"));

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", ids[0], ids[1])
                .then()
                .body("annulmentReason", equalTo("Primer motivo"));
    }

    @Test
    void annullingACertificateThroughAnotherCompanyOrAnUnknownIdIsRpt003() {
        String[] ids = companyWithJanuaryCertificate();
        String otherCompany = registerCompany(UUID.randomUUID());

        annul(otherCompany, ids[1], "Motivo").then().statusCode(404).body("code", equalTo("RPT-003"));
        annul(ids[0], UUID.randomUUID().toString(), "Motivo").then().statusCode(404).body("code", equalTo("RPT-003"));

        given().when().get("/tracked-companies/{companyId}/certificates/{id}", ids[0], ids[1])
                .then().body("status", equalTo("ISSUED"));
    }

    @Test
    void annullingRequiresAToken() {
        String[] ids = companyWithJanuaryCertificate();
        RestAssuredSetup.anonymous(port);

        annul(ids[0], ids[1], "Motivo").then().statusCode(401);
    }

    @Test
    void anAnnulledCertificateFreesItsPeriodAndTheNewOneBlocksOverlapsAgain() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerCompany(associationId);
        registerSigersol(associationId);
        String first = issuedCertificateId(companyId, "2026-01-01", "2026-01-31");
        issue(companyId, "2026-01-10", "2026-01-20").then().statusCode(409).body("code", equalTo("RPT-004"));

        annul(companyId, first, "Datos erróneos").then().statusCode(200);

        // RPT-004 ignores the annulled one: the very same period can be issued again...
        String second = issuedCertificateId(companyId, "2026-01-01", "2026-01-31");
        assertThat(second).isNotEqualTo(first);
        // ...and now the new, ISSUED one is the one that blocks.
        issue(companyId, "2026-01-10", "2026-01-20").then().statusCode(409).body("code", equalTo("RPT-004"));
        given().when().get("/tracked-companies/{companyId}/certificates", companyId)
                .then().body("content.size()", equalTo(2));
    }

    @Test
    void issuingToAnInactiveCompanyIsRpt015EvenWhenOtherChecksWouldAlsoFail() {
        UUID associationId = UUID.randomUUID();
        String companyId = registerCompany(associationId);
        // No SIGERSOL data at all: RPT-005 would apply, RPT-015 comes first.
        jdbc.update("UPDATE tracked_company SET status = 'INACTIVE' WHERE id = ?::uuid", companyId);

        issue(companyId, "2026-02-01", "2026-02-28")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-015"))
                .body("detail", equalTo("La empresa está inactiva: no se pueden emitir certificados"));

        // A backwards period is still RPT-009, before the company is even loaded.
        issue(companyId, "2026-02-28", "2026-02-01").then().statusCode(400).body("code", equalTo("RPT-009"));
        // An unknown company is RPT-001, never RPT-015.
        issue(UUID.randomUUID().toString(), "2026-02-01", "2026-02-28").then().statusCode(404)
                .body("code", equalTo("RPT-001"));
    }

    @Test
    void anInactiveCompanyKeepsItsCertificatesReadableAndAnnullable() {
        String[] ids = companyWithJanuaryCertificate();
        jdbc.update("UPDATE tracked_company SET status = 'INACTIVE' WHERE id = ?::uuid", ids[0]);

        given().when().get("/tracked-companies/{companyId}/certificates/{id}/pdf", ids[0], ids[1])
                .then().statusCode(200);
        annul(ids[0], ids[1], "Empresa dada de baja").then().statusCode(200);
    }

    @Test
    void theAnnulledPdfIsMarkedAnuladoAndTheCsvCarriesTheStateAndTheReason() throws IOException {
        String[] ids = companyWithJanuaryCertificate();
        annul(ids[0], ids[1], "=Datos erróneos").then().statusCode(200);

        byte[] pdf = given().when()
                .get("/tracked-companies/{companyId}/certificates/{id}/pdf", ids[0], ids[1])
                .then().statusCode(200).extract().asByteArray();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("ANULADO").contains("=Datos erróneos").contains("staff@example.com");
            assertThat(text).contains("Proyecto académico, sin validez legal");
        }

        String csv = new String(
                given().when().get("/tracked-companies/{companyId}/certificates/{id}/csv", ids[0], ids[1])
                        .then().statusCode(200).extract().asByteArray(),
                StandardCharsets.UTF_8);
        assertThat(csv).contains("estado,Anulado").contains("motivo_anulacion,'=Datos erróneos");
    }

    @Test
    void twoSimultaneousAnnulmentsOfTheSameCertificateExactlyOneWinsAndTheWinnersDataIsStored() throws Exception {
        String[] ids = companyWithJanuaryCertificate();
        String actorA = "01a0d8cb-f219-72a2-b006-5150f0e0d0aa";
        String actorB = "01a0d8cb-f219-72a2-b006-5150f0e0d0bb";

        // The default spec carries its own token and would win over each actor's own.
        RestAssuredSetup.anonymous(port);

        Callable<Response> byA = () -> annulAs(ids, actorA, "a@example.com", "Motivo de A");
        Callable<Response> byB = () -> annulAs(ids, actorB, "b@example.com", "Motivo de B");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Response> responses;
        try {
            List<Future<Response>> futures = List.of(
                    executor.submit(() -> afterBothReady(byA, readyLatch, startLatch)),
                    executor.submit(() -> afterBothReady(byB, readyLatch, startLatch)));
            readyLatch.await();
            startLatch.countDown();
            responses = List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        List<Response> winners = responses.stream().filter(r -> r.statusCode() == 200).toList();
        List<Response> losers = responses.stream()
                .filter(r -> r.statusCode() == 409 && "RPT-019".equals(r.jsonPath().getString("code")))
                .toList();
        assertThat(winners).hasSize(1);
        assertThat(losers).hasSize(1);

        // Whoever won, the stored actor and reason are the winner's, together.
        String winnerActor = winners.get(0).jsonPath().getString("annulledById");
        String expectedReason = winnerActor.equals(actorA) ? "Motivo de A" : "Motivo de B";
        given()
                .header("Authorization", "Bearer " + TestJwtTokens.validToken())
                .when()
                .get("/tracked-companies/{companyId}/certificates/{id}", ids[0], ids[1])
                .then()
                .body("status", equalTo("ANNULLED"))
                .body("annulledById", equalTo(winnerActor))
                .body("annulmentReason", equalTo(expectedReason));
    }

    private Response annulAs(String[] ids, String actorId, String email, String reason) {
        return given()
                .header("Authorization", "Bearer " + TestJwtTokens.validTokenFor(actorId, email))
                .contentType(ContentType.JSON)
                .body("{ \"reason\": \"%s\" }".formatted(reason))
                .when()
                .post("/tracked-companies/{companyId}/certificates/{id}/annul", ids[0], ids[1]);
    }

    private Response afterBothReady(Callable<Response> call, CountDownLatch readyLatch, CountDownLatch startLatch)
            throws Exception {
        readyLatch.countDown();
        startLatch.await();
        return call.call();
    }
}

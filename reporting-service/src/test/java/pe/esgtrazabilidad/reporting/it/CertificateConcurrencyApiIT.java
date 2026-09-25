package pe.esgtrazabilidad.reporting.it;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

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

import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The required (per SPEC-reporting-service.md, not optional) real
 * two-thread concurrency proof that RPT-004's EXCLUDE USING gist
 * constraint (Task 50) actually closes the race
 * CertificateService.issue()'s existsOverlapping() check (Task 52) cannot
 * close on its own -- same shape and same discipline as Task 46's
 * SigersolSyncConcurrencyApiIT.
 *
 * Goes through the real HTTP layer, not the service bean directly: only
 * the controller-scoped CertificateExceptionHandler translates the DB's
 * DataIntegrityViolationException into the typed 409 RPT-004 response.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class CertificateConcurrencyApiIT {

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

    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
    }

    private String registerTrackedCompanyRequest(String ruc, UUID associationId) {
        return """
                {
                  "name": "Empresa Concurrencia IT",
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
                  "hierarchyCompliancePercent": 80.0
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

    private Response callAfterBothReady(Callable<Response> call, CountDownLatch readyLatch, CountDownLatch startLatch)
            throws Exception {
        readyLatch.countDown();
        startLatch.await();
        return call.call();
    }

    @Test
    void twoConcurrentIssuancesForTheSameOverlappingPeriodOnlyOneSucceeds() throws Exception {
        UUID associationId = UUID.randomUUID();
        String companyId = registerTrackedCompany("20200000001", associationId);
        given()
                .contentType(ContentType.JSON)
                .body(registerSigersolSyncRequest(associationId, "2026-01-01", "2026-03-31"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);

        Callable<Response> issueFirstPeriod = () -> given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-01-01", "2026-01-31"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId);
        Callable<Response> issueOverlappingPeriod = () -> given()
                .contentType(ContentType.JSON)
                .body(issueCertificateRequest("2026-01-15", "2026-02-15"))
                .when()
                .post("/tracked-companies/{companyId}/certificates", companyId);

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Response> responses;
        try {
            List<Future<Response>> futures = List.of(
                    executor.submit(() -> callAfterBothReady(issueFirstPeriod, readyLatch, startLatch)),
                    executor.submit(() -> callAfterBothReady(issueOverlappingPeriod, readyLatch, startLatch)));
            readyLatch.await();
            startLatch.countDown();
            responses = List.of(futures.get(0).get(10, TimeUnit.SECONDS), futures.get(1).get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdown();
        }

        long successCount =
                responses.stream().filter(response -> response.statusCode() == 201).count();
        long conflictCount = responses.stream()
                .filter(response -> response.statusCode() == 409
                        && "RPT-004".equals(response.jsonPath().getString("code")))
                .count();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);

        given()
                .when()
                .get("/tracked-companies/{companyId}/certificates", companyId)
                .then()
                .statusCode(200)
                .body("content.size()", org.hamcrest.Matchers.equalTo(1));
    }
}

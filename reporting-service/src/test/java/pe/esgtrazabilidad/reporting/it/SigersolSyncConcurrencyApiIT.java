package pe.esgtrazabilidad.reporting.it;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;

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

import pe.esgtrazabilidad.reporting.sigersolsync.port.out.SigersolSyncRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The required (per SPEC-reporting-service.md, not optional) real
 * two-thread concurrency proof that RPT-006's EXCLUDE USING gist
 * constraint (Task 44) actually closes the race a naive
 * existsOverlapping()-then-save() check (Task 45) cannot -- same
 * discipline cross-service-events' Task 40 established after a Cowork-
 * caught gap, applied here proactively.
 *
 * Goes through the real HTTP layer (not the service bean directly): only
 * the controller-scoped SigersolSyncExceptionHandler translates the DB's
 * DataIntegrityViolationException into the typed 409 RPT-006 response --
 * calling the service method directly would surface the raw exception
 * instead, which isn't what the acceptance criteria (loser gets RPT-006)
 * actually asks for.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SigersolSyncConcurrencyApiIT {

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
    private SigersolSyncRepository sigersolSyncRepository;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    private String registerRequest(UUID associationId, String periodStart, String periodEnd) {
        return """
                {
                  "associationId": "%s",
                  "periodStart": "%s",
                  "periodEnd": "%s",
                  "hierarchyCompliancePercent": 80.0
                }
                """.formatted(associationId, periodStart, periodEnd);
    }

    @Test
    void twoConcurrentRegistrationsForTheSameOverlappingPeriodOnlyOneSucceeds() throws Exception {
        UUID associationId = UUID.randomUUID();
        Callable<Response> registerFirstPeriod = () -> given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-01-01", "2026-01-31"))
                .when()
                .post("/sigersol-syncs");
        Callable<Response> registerOverlappingPeriod = () -> given()
                .contentType(ContentType.JSON)
                .body(registerRequest(associationId, "2026-01-15", "2026-02-15"))
                .when()
                .post("/sigersol-syncs");

        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Response> responses;
        try {
            List<Future<Response>> futures = List.of(
                    executor.submit(() -> callAfterBothReady(registerFirstPeriod, readyLatch, startLatch)),
                    executor.submit(() -> callAfterBothReady(registerOverlappingPeriod, readyLatch, startLatch)));
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
                        && "RPT-006".equals(response.jsonPath().getString("code")))
                .count();
        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(1);
        assertThat(sigersolSyncRepository.findAll(associationId, org.springframework.data.domain.Pageable.unpaged())
                        .getTotalElements())
                .isEqualTo(1);
    }

    private Response callAfterBothReady(Callable<Response> call, CountDownLatch readyLatch, CountDownLatch startLatch)
            throws Exception {
        readyLatch.countDown();
        startLatch.await();
        return call.call();
    }
}

package pe.esgtrazabilidad.reporting.it;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

import io.restassured.http.ContentType;
import io.restassured.response.Response;

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

import pe.esgtrazabilidad.reporting.it.support.FakeRucLookup;
import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;
import pe.esgtrazabilidad.reporting.it.support.TestRucs;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * M4 lifecycle over real HTTP: edit, deactivate/activate with a reason and a
 * history row (the actor from the token), logical delete that frees the RUC,
 * RPT-017/018, and the two races (same-status changes, same-RUC registers).
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class TrackedCompanyLifecycleApiIT {

    private static final AtomicLong RUC_SEQUENCE = new AtomicLong(72_000_000L);
    private static final String ACTOR_ID = "01a0d8cb-f219-72a2-b006-5150f0e0d222";

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

    @Autowired
    private FakeRucLookup sunat;

    @BeforeEach
    void setUp() {
        RestAssuredSetup.authenticated(port);
        sunat.reset();
    }

    private static String nextRuc() {
        return TestRucs.fromSequence(RUC_SEQUENCE.incrementAndGet());
    }

    private String register(String ruc, UUID associationId) {
        return given().contentType(ContentType.JSON)
                .body("{ \"ruc\": \"%s\", \"associationId\": \"%s\" }".formatted(ruc, associationId))
                .when().post("/tracked-companies")
                .then().statusCode(201).extract().path("id");
    }

    private Response change(String id, String action, String reason) {
        return given().contentType(ContentType.JSON).body("{ \"reason\": \"%s\" }".formatted(reason))
                .when().patch("/tracked-companies/{id}/" + action, id);
    }

    private Response delete(String id, String reason) {
        return given().contentType(ContentType.JSON).body("{ \"reason\": \"%s\" }".formatted(reason))
                .when().delete("/tracked-companies/{id}", id);
    }

    private Response edit(String id, String body) {
        return given().contentType(ContentType.JSON).body(body).when().patch("/tracked-companies/{id}", id);
    }

    /** A company with a SIGERSOL record for 2026 and an ISSUED certificate for January. */
    private String companyWithCertificate(UUID associationId) {
        String id = register(nextRuc(), associationId);
        given().contentType(ContentType.JSON)
                .body("""
                        { "associationId": "%s", "periodStart": "2026-01-01", "periodEnd": "2026-12-31",
                          "hierarchyCompliancePercent": 80.0 }
                        """.formatted(associationId))
                .when().post("/sigersol-syncs").then().statusCode(201);
        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-01-01\", \"periodEnd\": \"2026-01-31\" }")
                .when().post("/tracked-companies/{id}/certificates", id).then().statusCode(201);
        return id;
    }

    @Test
    void deactivatingAndReactivatingWriteTheHistoryWithTheActorFromTheToken() {
        String id = register(nextRuc(), UUID.randomUUID());
        RestAssuredSetup.anonymous(port);
        String token = TestJwtTokens.validTokenFor(ACTOR_ID, "ana@example.com");

        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{ \"reason\": \"  Cerró el local  \", \"actorId\": \"ignored\" }")
                .when().patch("/tracked-companies/{id}/deactivate", id)
                .then().statusCode(200).body("status", equalTo("INACTIVE"));
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body("{ \"reason\": \"Reabrió\" }")
                .when().patch("/tracked-companies/{id}/activate", id)
                .then().statusCode(200).body("status", equalTo("ACTIVE"));

        List<Map<String, Object>> history = jdbc.queryForList(
                "SELECT from_status, to_status, reason, actor_id::text AS actor_id, actor_email "
                        + "FROM tracked_company_status_change WHERE tracked_company_id = ?::uuid ORDER BY changed_at",
                id);
        assertThat(history).hasSize(2);
        assertThat(history.get(0)).containsEntry("from_status", "ACTIVE").containsEntry("to_status", "INACTIVE")
                .containsEntry("reason", "Cerró el local").containsEntry("actor_id", ACTOR_ID)
                .containsEntry("actor_email", "ana@example.com");
        assertThat(history.get(1)).containsEntry("from_status", "INACTIVE").containsEntry("to_status", "ACTIVE");
    }

    @Test
    void theReasonIsRequiredAndTheSameStatusIsRpt018() {
        String id = register(nextRuc(), UUID.randomUUID());

        given().contentType(ContentType.JSON).body("{ \"reason\": \"   \" }")
                .when().patch("/tracked-companies/{id}/deactivate", id)
                .then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        change(id, "activate", "ya estaba").then().statusCode(409).body("code", equalTo("RPT-018"));
        change(id, "deactivate", "uno").then().statusCode(200);
        change(id, "deactivate", "dos").then().statusCode(409).body("code", equalTo("RPT-018"));
        change(UUID.randomUUID().toString(), "deactivate", "x").then().statusCode(404).body("code", equalTo("RPT-001"));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracked_company_status_change WHERE tracked_company_id = ?::uuid",
                        Integer.class, id))
                .as("only the change that happened leaves history")
                .isEqualTo(1);
    }

    @Test
    void twoSimultaneousDeactivationsGiveExactlyOne200AndOneRpt018() throws Exception {
        String id = register(nextRuc(), UUID.randomUUID());

        List<Response> answers = race(2, () -> change(id, "deactivate", "carrera"));

        assertThat(answers).extracting(Response::statusCode).containsExactlyInAnyOrder(200, 409);
        assertThat(answers.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().path("code").toString())
                .isEqualTo("RPT-018");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracked_company_status_change WHERE tracked_company_id = ?::uuid",
                        Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void anInactiveCompanyIsRefusedCertificatesButStaysReadable() {
        String id = register(nextRuc(), UUID.randomUUID());
        change(id, "deactivate", "baja").then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-02-01\", \"periodEnd\": \"2026-02-28\" }")
                .when().post("/tracked-companies/{id}/certificates", id)
                .then().statusCode(409).body("code", equalTo("RPT-015"));
        given().when().get("/tracked-companies/{id}", id).then().statusCode(200).body("status", equalTo("INACTIVE"));
    }

    @Test
    void deletingIsLogicalHidesTheCompanyAndFreesTheRuc() {
        String ruc = nextRuc();
        String id = register(ruc, UUID.randomUUID());
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.validTokenFor(ACTOR_ID, "ana@example.com"))
                .contentType(ContentType.JSON).body("{ \"reason\": \"Duplicada\" }")
                .when().delete("/tracked-companies/{id}", id).then().statusCode(204);
        RestAssuredSetup.authenticated(port);

        given().when().get("/tracked-companies/{id}", id).then().statusCode(404).body("code", equalTo("RPT-001"));
        given().queryParam("size", 100).when().get("/tracked-companies")
                .then().statusCode(200).body("content.id", not(hasItem(id)));
        change(id, "deactivate", "x").then().statusCode(404).body("code", equalTo("RPT-001"));
        delete(id, "otra vez").then().statusCode(404).body("code", equalTo("RPT-001"));
        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-02-01\", \"periodEnd\": \"2026-02-28\" }")
                .when().post("/tracked-companies/{id}/certificates", id).then().statusCode(404);

        Map<String, Object> row = jdbc.queryForMap(
                "SELECT deleted_at IS NOT NULL AS deleted, deleted_by_id::text AS by_id, deleted_by_email, "
                        + "deletion_reason FROM tracked_company WHERE id = ?::uuid", id);
        assertThat(row).containsEntry("deleted", true).containsEntry("by_id", ACTOR_ID)
                .containsEntry("deleted_by_email", "ana@example.com").containsEntry("deletion_reason", "Duplicada");

        // The RUC is free again: registering it makes a new, different company.
        String again = register(ruc, UUID.randomUUID());
        assertThat(again).isNotEqualTo(id);
    }

    @Test
    void aCompanyWithCertificatesCannotBeDeletedEvenIfAllAreAnnulled() {
        String id = companyWithCertificate(UUID.randomUUID());
        delete(id, "intento").then().statusCode(409).body("code", equalTo("RPT-017"));

        String certificateId = given().when().get("/tracked-companies/{id}/certificates", id).path("content[0].id");
        given().contentType(ContentType.JSON).body("{ \"reason\": \"error\" }")
                .when().post("/tracked-companies/{id}/certificates/{cid}/annul", id, certificateId)
                .then().statusCode(200);

        delete(id, "intento otra vez").then().statusCode(409).body("code", equalTo("RPT-017"));
        given().when().get("/tracked-companies/{id}", id).then().statusCode(200);
    }

    @Test
    void theContactIsEditedPartiallyAndABlankClearsIt() {
        String id = register(nextRuc(), UUID.randomUUID());

        edit(id, "{ \"contactName\": \"Rosa Quispe\", \"contactEmail\": \"rosa@example.org\", \"contactPhone\": \"999\" }")
                .then().statusCode(200).body("contactName", equalTo("Rosa Quispe"));
        edit(id, "{ \"contactPhone\": \"  \" }").then().statusCode(200)
                .body("contactPhone", nullValue())
                .body("contactName", equalTo("Rosa Quispe"))
                .body("contactEmail", equalTo("rosa@example.org"));
        edit(id, "{ \"contactEmail\": \"no-es-correo\" }").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        edit(UUID.randomUUID().toString(), "{ \"contactName\": \"x\" }").then().statusCode(404);
    }

    @Test
    void theRucAndAssociationChangeOnlyWhileThereAreNoCertificates() {
        UUID association = UUID.randomUUID();
        String id = register(nextRuc(), association);
        String newRuc = nextRuc();
        sunat.answer(newRuc, new RucLookupResult.Found("NUEVA RAZON S.A.C.", "ACTIVO", "HABIDO", null));
        UUID newAssociation = UUID.randomUUID();

        edit(id, "{ \"ruc\": \"%s\", \"associationId\": \"%s\" }".formatted(newRuc, newAssociation))
                .then().statusCode(200)
                .body("ruc", equalTo(newRuc))
                .body("associationId", equalTo(newAssociation.toString()))
                .body("name", equalTo("NUEVA RAZON S.A.C."))
                .body("verification", equalTo("VERIFIED"));

        String withCertificate = companyWithCertificate(UUID.randomUUID());
        edit(withCertificate, "{ \"ruc\": \"%s\" }".formatted(nextRuc())).then().statusCode(409).body("code", equalTo("RPT-017"));
        edit(withCertificate, "{ \"associationId\": \"%s\" }".formatted(UUID.randomUUID()))
                .then().statusCode(409).body("code", equalTo("RPT-017"));
        // Same values are not a change; the contact is always editable.
        String current = given().when().get("/tracked-companies/{id}", withCertificate).path("ruc");
        edit(withCertificate, "{ \"ruc\": \"%s\", \"contactName\": \"Luis\" }".formatted(current))
                .then().statusCode(200).body("contactName", equalTo("Luis"));
    }

    @Test
    void aNewRucIsCheckedLikeARegistration() {
        String id = register(nextRuc(), UUID.randomUUID());
        String taken = nextRuc();
        register(taken, UUID.randomUUID());
        String unknown = nextRuc();
        sunat.answer(unknown, new RucLookupResult.NotFound());

        edit(id, "{ \"ruc\": \"20131312950\" }").then().statusCode(400).body("code", equalTo("RPT-010"));
        edit(id, "{ \"ruc\": \"%s\" }".formatted(taken)).then().statusCode(409).body("code", equalTo("RPT-002"));
        edit(id, "{ \"ruc\": \"%s\" }".formatted(unknown)).then().statusCode(422).body("code", equalTo("RPT-012"));
    }

    @Test
    void twoSimultaneousRegistrationsOfTheSameRucGiveOne201AndOneRpt002FromTheIndex() throws Exception {
        String ruc = nextRuc();

        List<Response> answers = race(2, () -> given().contentType(ContentType.JSON)
                .body("{ \"ruc\": \"%s\", \"associationId\": \"%s\" }".formatted(ruc, UUID.randomUUID()))
                .when().post("/tracked-companies"));

        assertThat(answers).extracting(Response::statusCode).containsExactlyInAnyOrder(201, 409);
        assertThat(answers.stream().filter(r -> r.statusCode() == 409).findFirst().orElseThrow().path("code").toString())
                .isEqualTo("RPT-002");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracked_company WHERE ruc = ? AND deleted_at IS NULL", Integer.class, ruc))
                .isEqualTo(1);
    }

    private List<Response> race(int threads, Callable<Response> call) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch go = new CountDownLatch(1);
            List<Future<Response>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return call.call();
                }));
            }
            ready.await();
            go.countDown();
            List<Response> answers = new java.util.ArrayList<>();
            for (Future<Response> future : futures) {
                answers.add(future.get());
            }
            return answers;
        } finally {
            pool.shutdownNow();
        }
    }
}

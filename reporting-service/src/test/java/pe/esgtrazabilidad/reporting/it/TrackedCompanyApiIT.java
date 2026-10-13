package pe.esgtrazabilidad.reporting.it;

import java.util.UUID;

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

import pe.esgtrazabilidad.reporting.it.support.FakeRucLookup;
import pe.esgtrazabilidad.reporting.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.reporting.it.support.TestRucs;
import pe.esgtrazabilidad.reporting.it.support.TestJwtTokens;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class TrackedCompanyApiIT {

    // Valid check digits, distinct from the literals of the older tests (answers are cached in the database).
    private static final String RUC_DUPLICATE = TestRucs.fromSequence(70000001);
    private static final String RUC_FALLBACK = TestRucs.fromSequence(70000002);
    private static final String RUC_ANSWERING = TestRucs.fromSequence(70000003);
    private static final String RUC_CACHED = TestRucs.fromSequence(70000004);
    private static final String RUC_TO_VERIFY = TestRucs.fromSequence(70000005);
    private static final String RUC_STAYS_UNVERIFIED = TestRucs.fromSequence(70000006);
    private static final String RUC_VERIFIED_LATER_BAJA = TestRucs.fromSequence(70000007);
    private static final String RUC_INACTIVE_UNVERIFIED = TestRucs.fromSequence(70000008);

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
    private FakeRucLookup sunat;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpRestAssured() {
        RestAssuredSetup.authenticated(port);
        sunat.reset();
    }

    private String registerTrackedCompanyRequest(String ruc, UUID associationId) {
        return """
                {
                  "ruc": "%s",
                  "associationId": "%s"
                }
                """.formatted(ruc, associationId);
    }

    @Test
    void registerGetAndListATrackedCompanyWithTheNameFromSunat() {
        UUID associationId = UUID.randomUUID();
        sunat.answer(
                "20111111121", new RucLookupResult.Found("RECICLADORA DEL SUR S.A.C.", "ACTIVO", "HABIDO", "150131"));
        String id = given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20111111121", associationId))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .body("name", equalTo("RECICLADORA DEL SUR S.A.C."))
                .body("ruc", equalTo("20111111121"))
                .body("associationId", equalTo(associationId.toString()))
                .body("status", equalTo("ACTIVE"))
                .body("verification", equalTo("VERIFIED"))
                .body("sunatEstado", equalTo("ACTIVO"))
                .body("sunatCondicion", equalTo("HABIDO"))
                .body("sunatCheckedAt", notNullValue())
                .body("warning", nullValue())
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/tracked-companies/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("name", equalTo("RECICLADORA DEL SUR S.A.C."));

        given()
                .when()
                .get("/tracked-companies")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void theContactIsStoredAndTheDeprecatedNameInTheBodyIsIgnored() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        { "name": "Nombre que el cliente quiere imponer", "ruc": "20333333334",
                          "associationId": "%s", "contactName": "Rosa Quispe",
                          "contactEmail": "rosa@example.org", "contactPhone": "999111222" }
                        """.formatted(UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .body("name", equalTo("Empresa SUNAT 20333333334"))
                .body("contactName", equalTo("Rosa Quispe"))
                .body("contactEmail", equalTo("rosa@example.org"))
                .body("contactPhone", equalTo("999111222"));
    }

    @Test
    void registeringWithADuplicateRucReturnsConflict() {
        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20222222223", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201);

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20222222223", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(409)
                .body("code", equalTo("RPT-002"));
    }

    @Test
    void aDuplicateRucDoesNotSpendASunatCall() {
        String body = registerTrackedCompanyRequest(RUC_DUPLICATE, UUID.randomUUID());
        given().contentType(ContentType.JSON).body(body).when().post("/tracked-companies").then().statusCode(201);
        int callsAfterFirst = sunat.calls();

        given().contentType(ContentType.JSON).body(body).when().post("/tracked-companies")
                .then().statusCode(409).body("code", equalTo("RPT-002"));

        assertThat(sunat.calls()).isEqualTo(callsAfterFirst);
    }

    private String unverifiedBody(String ruc) {
        return """
                { "ruc": "%s", "name": "Escrita a mano S.A.C.", "associationId": "%s",
                  "contactName": "Rosa Quispe" }
                """.formatted(ruc, UUID.randomUUID());
    }

    @Test
    void theFallbackStoresAnUnverifiedCompanyWhenSunatDoesNotAnswerAndItCannotReceiveCertificates() {
        sunat.unavailableAll(true);

        String id = given().contentType(ContentType.JSON).body(unverifiedBody(RUC_FALLBACK))
                .when().post("/tracked-companies/unverified")
                .then().statusCode(201)
                .body("verification", equalTo("UNVERIFIED"))
                .body("name", equalTo("Escrita a mano S.A.C."))
                .body("sunatCheckedAt", nullValue())
                .body("contactName", equalTo("Rosa Quispe"))
                .extract().path("id");

        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-10-01\", \"periodEnd\": \"2026-10-31\" }")
                .when().post("/tracked-companies/{id}/certificates", id)
                .then().statusCode(409).body("code", equalTo("RPT-016"));
    }

    @Test
    void theFallbackIsRefusedWithRpt014WhenSunatAnswers() {
        given().contentType(ContentType.JSON).body(unverifiedBody(RUC_ANSWERING))
                .when().post("/tracked-companies/unverified")
                .then().statusCode(409).body("code", equalTo("RPT-014"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracked_company WHERE ruc = '" + RUC_ANSWERING + "'", Integer.class))
                .isZero();
    }

    @Test
    void theFallbackRepeatsTheLookupInsteadOfTrustingTheCache() {
        sunat.answer(RUC_CACHED, new RucLookupResult.NotFound());
        given().contentType(ContentType.JSON).body(registerTrackedCompanyRequest(RUC_CACHED, UUID.randomUUID()))
                .when().post("/tracked-companies").then().statusCode(422); // NotFound is now cached
        sunat.unavailableAll(true);

        // Reading the cache would answer NotFound (RPT-014); the fresh re-check sees the outage.
        given().contentType(ContentType.JSON).body(unverifiedBody(RUC_CACHED))
                .when().post("/tracked-companies/unverified")
                .then().statusCode(201);
    }

    @Test
    void anInactiveAndUnverifiedCompanyAnswersRpt015BeforeRpt016() {
        sunat.unavailableAll(true);
        String id = given().contentType(ContentType.JSON).body(unverifiedBody(RUC_INACTIVE_UNVERIFIED))
                .when().post("/tracked-companies/unverified").then().statusCode(201).extract().path("id");
        given().contentType(ContentType.JSON).body("{ \"reason\": \"Prueba\" }")
                .when().patch("/tracked-companies/{id}/deactivate", id).then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-10-01\", \"periodEnd\": \"2026-10-31\" }")
                .when().post("/tracked-companies/{id}/certificates", id)
                .then().statusCode(409).body("code", equalTo("RPT-015"));
    }

    @Test
    void refreshVerifiesAnUnverifiedCompanyAndTheCertificateGateOpens() {
        sunat.unavailableAll(true);
        String id = given().contentType(ContentType.JSON).body(unverifiedBody(RUC_TO_VERIFY))
                .when().post("/tracked-companies/unverified").then().statusCode(201).extract().path("id");
        sunat.unavailableAll(false);
        sunat.answer(RUC_TO_VERIFY, new RucLookupResult.Found("RAZON REAL S.A.", "ACTIVO", "HABIDO", "150101"));

        given().when().post("/tracked-companies/{id}/sunat-refresh", id)
                .then().statusCode(200)
                .body("verification", equalTo("VERIFIED"))
                .body("name", equalTo("RAZON REAL S.A."))
                .body("sunatEstado", equalTo("ACTIVO"))
                .body("sunatCheckedAt", notNullValue());

        given().contentType(ContentType.JSON)
                .body("{ \"periodStart\": \"2026-10-01\", \"periodEnd\": \"2026-10-31\" }")
                .when().post("/tracked-companies/{id}/certificates", id)
                .then().body("code", org.hamcrest.Matchers.not(equalTo("RPT-016")));
    }

    @Test
    void refreshChangesNothingWhenSunatIsDownUnknownOrTheUnverifiedCompanyIsInBaja() {
        sunat.unavailableAll(true);
        String id = given().contentType(ContentType.JSON).body(unverifiedBody(RUC_STAYS_UNVERIFIED))
                .when().post("/tracked-companies/unverified").then().statusCode(201).extract().path("id");

        given().when().post("/tracked-companies/{id}/sunat-refresh", id)
                .then().statusCode(503).body("code", equalTo("RPT-011"));

        sunat.unavailableAll(false);
        sunat.answer(RUC_STAYS_UNVERIFIED, new RucLookupResult.NotFound());
        given().when().post("/tracked-companies/{id}/sunat-refresh", id)
                .then().statusCode(422).body("code", equalTo("RPT-012"));

        sunat.answer(RUC_STAYS_UNVERIFIED, new RucLookupResult.Found("CERRADA", "BAJA DEFINITIVA", "NO HABIDO", null));
        given().when().post("/tracked-companies/{id}/sunat-refresh", id)
                .then().statusCode(422).body("code", equalTo("RPT-013"));

        given().when().get("/tracked-companies/{id}", id)
                .then().body("verification", equalTo("UNVERIFIED")).body("name", equalTo("Escrita a mano S.A.C."));
    }

    @Test
    void refreshOfAVerifiedCompanyStoresSunatsNewEstadoEvenInBaja() {
        String id = given().contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest(RUC_VERIFIED_LATER_BAJA, UUID.randomUUID()))
                .when().post("/tracked-companies").then().statusCode(201).extract().path("id");
        sunat.answer(RUC_VERIFIED_LATER_BAJA, new RucLookupResult.Found("NUEVA RAZON", "BAJA DEFINITIVA", "NO HABIDO", null));

        given().when().post("/tracked-companies/{id}/sunat-refresh", id)
                .then().statusCode(200)
                .body("verification", equalTo("VERIFIED"))
                .body("name", equalTo("NUEVA RAZON"))
                .body("sunatEstado", equalTo("BAJA DEFINITIVA"))
                .body("warning", notNullValue());
    }

    @Test
    void refreshOfAMissingCompanyIsNotFound() {
        given().when().post("/tracked-companies/{id}/sunat-refresh", "00000000-0000-0000-0000-000000000000")
                .then().statusCode(404).body("code", equalTo("RPT-001"));
    }

    @Test
    void gettingAMissingTrackedCompanyReturnsNotFound() {
        given()
                .when()
                .get("/tracked-companies/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("RPT-001"));
    }

    @Test
    void registeringWithANonNumericRucReturnsAValidationError() {
        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("1234567890a", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void aRucWithABadCheckDigitIsRejectedLocallyWithoutCallingSunat() {
        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20131312950", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(400)
                .body("code", equalTo("RPT-010"))
                .body("detail", equalTo("El RUC no es válido"));
        assertThat(sunat.calls()).isZero();
    }

    @Test
    void whenSunatDoesNotAnswerRegistrationIs503WithRpt011AndNothingIsStored() {
        sunat.unavailableAll(true);

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20444444445", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(503)
                .body("code", equalTo("RPT-011"));

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM tracked_company WHERE ruc = '20444444445'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM ruc_lookup_cache WHERE ruc = '20444444445'", Integer.class))
                .as("an outage is never cached")
                .isZero();
    }

    @Test
    void aRucSunatDoesNotKnowIs422WithRpt012() {
        sunat.answer("20555555556", new RucLookupResult.NotFound());

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20555555556", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(422)
                .body("code", equalTo("RPT-012"));
    }

    @Test
    void aRucInBajaDefinitivaIs422WithRpt013() {
        sunat.answer("20666666667", new RucLookupResult.Found("EMPRESA CERRADA", "BAJA DEFINITIVA", "NO HABIDO", null));

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20666666667", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(422)
                .body("code", equalTo("RPT-013"));
    }

    @Test
    void aNoHabidoTaxpayerIsRegisteredWithAWarning() {
        sunat.answer("20888888889", new RucLookupResult.Found("EMPRESA ILOCALIZABLE", "ACTIVO", "NO HABIDO", null));

        given()
                .contentType(ContentType.JSON)
                .body(registerTrackedCompanyRequest("20888888889", UUID.randomUUID()))
                .when()
                .post("/tracked-companies")
                .then()
                .statusCode(201)
                .body("verification", equalTo("VERIFIED"))
                .body("sunatCondicion", equalTo("NO HABIDO"))
                .body("warning", notNullValue());
    }

    @Test
    void answersAreCachedSoASecondLookupOfTheSameRucDoesNotCallTheProvider() {
        sunat.answer("20999999990", new RucLookupResult.NotFound());
        String body = registerTrackedCompanyRequest("20999999990", UUID.randomUUID());

        for (int attempt = 0; attempt < 2; attempt++) {
            given().contentType(ContentType.JSON).body(body).when().post("/tracked-companies")
                    .then().statusCode(422).body("code", equalTo("RPT-012"));
        }

        assertThat(sunat.calls()).isEqualTo(1);
    }

    @Test
    void anExpiredCacheEntryIsAskedAgain() {
        sunat.answer("20100000009", new RucLookupResult.NotFound());
        String body = registerTrackedCompanyRequest("20100000009", UUID.randomUUID());
        given().contentType(ContentType.JSON).body(body).when().post("/tracked-companies")
                .then().statusCode(422);

        jdbc.update("UPDATE ruc_lookup_cache SET fetched_at = fetched_at - interval '25 hours' WHERE ruc = '20100000009'");
        sunat.answer("20100000009", new RucLookupResult.Found("YA EXISTE AHORA", "ACTIVO", "HABIDO", null));

        given().contentType(ContentType.JSON).body(body).when().post("/tracked-companies")
                .then().statusCode(201).body("name", equalTo("YA EXISTE AHORA"));
        assertThat(sunat.calls()).isEqualTo(2);
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/tracked-companies")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }
}

package pe.esgtrazabilidad.reporting.it;

import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

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
        given()
                .contentType(ContentType.JSON)
                .body(registerSigersolSyncRequest(associationId, "2026-02-01", "2026-02-28"))
                .when()
                .post("/sigersol-syncs")
                .then()
                .statusCode(201);

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
}

package pe.esgtrazabilidad.recycler.it;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CertificationApiIT {

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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    private String createAssociationRequest(String ruc) {
        return """
                {
                  "name": "Asociación para certificaciones IT",
                  "ruc": "%s",
                  "registrationNumber": "REG-IT-200",
                  "address": "Dirección de prueba",
                  "contactEmail": "it@test.pe",
                  "contactPhone": "999999999"
                }
                """.formatted(ruc);
    }

    private String createCertificationRequest(String issuedDate, String expirationDate) {
        return """
                {
                  "certificationType": "ISO 14001",
                  "issuedDate": "%s",
                  "expirationDate": "%s"
                }
                """.formatted(issuedDate, expirationDate);
    }

    private String createAssociation(String ruc) {
        return given()
                .contentType(ContentType.JSON)
                .body(createAssociationRequest(ruc))
                .when()
                .post("/associations")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    @Test
    void createGetAndListACertification() {
        String associationId = createAssociation("20101010101");
        String issuedDate = LocalDate.now().minusDays(30).toString();
        String expirationDate = LocalDate.now().plusYears(1).toString();

        String certificationId = given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expirationDate))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(201)
                .body("certificationType", equalTo("ISO 14001"))
                .body("associationId", equalTo(associationId))
                .body("expired", equalTo(false))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/associations/{associationId}/certifications/{id}", associationId, certificationId)
                .then()
                .statusCode(200)
                .body("id", equalTo(certificationId))
                .body("expired", equalTo(false));

        given()
                .when()
                .get("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void creatingUnderANonexistentAssociationReturnsNotFound() {
        String issuedDate = LocalDate.now().minusDays(30).toString();
        String expirationDate = LocalDate.now().plusYears(1).toString();

        given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expirationDate))
                .when()
                .post("/associations/{associationId}/certifications", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("CER-002"));
    }

    @Test
    void creatingWithAnInvalidDateRangeReturnsBadRequest() {
        String associationId = createAssociation("20202020202");
        String sameDay = LocalDate.now().toString();

        given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(sameDay, sameDay))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(400)
                .body("code", equalTo("CER-003"));
    }

    @Test
    void gettingAMissingCertificationReturnsNotFound() {
        String associationId = createAssociation("20303030303");

        given()
                .when()
                .get(
                        "/associations/{associationId}/certifications/{id}",
                        associationId,
                        "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("CER-001"));
    }

    @Test
    void gettingACertificationThroughAnotherAssociationsPathReturnsNotFound() {
        String associationAId = createAssociation("20404040404");
        String associationBId = createAssociation("20505050505");
        String issuedDate = LocalDate.now().minusDays(30).toString();
        String expirationDate = LocalDate.now().plusYears(1).toString();

        String certificationBId = given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expirationDate))
                .when()
                .post("/associations/{associationId}/certifications", associationBId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .get("/associations/{associationId}/certifications/{id}", associationAId, certificationBId)
                .then()
                .statusCode(404)
                .body("code", equalTo("CER-001"));
    }

    @Test
    void renewingACertificationExtendsItsExpirationDateAndClearsExpiredStatus() {
        String associationId = createAssociation("20707070707");
        String issuedDate = LocalDate.now().minusYears(2).toString();
        String expiredDate = LocalDate.now().minusDays(1).toString();

        String certificationId = given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expiredDate))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(201)
                .body("expired", equalTo(true))
                .extract()
                .path("id");

        String newExpirationDate = LocalDate.now().plusYears(1).toString();
        String renewRequest = """
                {
                  "newExpirationDate": "%s"
                }
                """.formatted(newExpirationDate);

        given()
                .contentType(ContentType.JSON)
                .body(renewRequest)
                .when()
                .patch("/associations/{associationId}/certifications/{id}/renew", associationId, certificationId)
                .then()
                .statusCode(200)
                .body("expirationDate", equalTo(newExpirationDate))
                .body("expired", equalTo(false));

        // Real evidence, not assumption: CertificationServiceTest (Mockito)
        // only proves renew() calls eventPublisher.publish() -- it can't prove
        // the outbox row and the certification update actually landed together
        // in one real transaction against Postgres. Same direct-SQL
        // verification pattern as CollectionRecordApiIT
        // .creatingARecordWritesAPendingOutboxEntry (Task 28) and
        // CertificationRepositoryAdapterIT
        // .findExpiredAndNotYetNotifiedReturnsOnlyExpiredCertificationsNeverNotified
        // (Task 33).
        Map<String, Object> outboxEntry = jdbcTemplate.queryForMap(
                "SELECT status, payload_json FROM outbox_event_recycler "
                        + "WHERE event_type = 'CertificationRenewedEvent' AND payload_json LIKE ?",
                "%" + certificationId + "%");
        assertThat(outboxEntry.get("status")).isEqualTo("NEW");
        assertThat((String) outboxEntry.get("payload_json")).contains(certificationId);
    }

    @Test
    void renewingWithADateNotAfterTheIssuedDateReturnsBadRequest() {
        String associationId = createAssociation("20808080808");
        String issuedDate = LocalDate.now().minusDays(30).toString();
        String expirationDate = LocalDate.now().plusYears(1).toString();

        String certificationId = given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expirationDate))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        String renewRequest = """
                {
                  "newExpirationDate": "%s"
                }
                """.formatted(issuedDate);

        given()
                .contentType(ContentType.JSON)
                .body(renewRequest)
                .when()
                .patch("/associations/{associationId}/certifications/{id}/renew", associationId, certificationId)
                .then()
                .statusCode(400)
                .body("code", equalTo("CER-003"));
    }

    @Test
    void anExpiredCertificationIsReportedAsExpired() {
        String associationId = createAssociation("20606060606");
        String issuedDate = LocalDate.now().minusYears(2).toString();
        String expiredDate = LocalDate.now().minusDays(1).toString();

        given()
                .contentType(ContentType.JSON)
                .body(createCertificationRequest(issuedDate, expiredDate))
                .when()
                .post("/associations/{associationId}/certifications", associationId)
                .then()
                .statusCode(201)
                .body("expired", equalTo(true));
    }
}

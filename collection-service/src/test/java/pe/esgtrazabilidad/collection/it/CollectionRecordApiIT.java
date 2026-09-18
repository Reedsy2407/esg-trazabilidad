package pe.esgtrazabilidad.collection.it;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import pe.esgtrazabilidad.collection.association.domain.BlockedAssociation;
import pe.esgtrazabilidad.collection.association.port.out.BlockedAssociationRepository;
import pe.esgtrazabilidad.kernel.events.OutboxEntry;
import pe.esgtrazabilidad.kernel.events.OutboxRepository;
import pe.esgtrazabilidad.kernel.events.OutboxStatus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CollectionRecordApiIT {

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
    private OutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BlockedAssociationRepository blockedAssociationRepository;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    private String createNeighborRequest(String fullName) {
        return """
                {
                  "fullName": "%s",
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "district": "Surco"
                }
                """.formatted(fullName);
    }

    private String createNeighbor(String fullName) {
        return given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest(fullName))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createScheduleRequest(String dayOfWeek, String time) {
        return """
                {
                  "dayOfWeek": "%s",
                  "time": "%s"
                }
                """.formatted(dayOfWeek, time);
    }

    private String createSchedule(String neighborId, String dayOfWeek, String time) {
        return given()
                .contentType(ContentType.JSON)
                .body(createScheduleRequest(dayOfWeek, time))
                .when()
                .post("/neighbors/{neighborId}/schedules", neighborId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    private String createRecordRequest(String scheduleId, String associationId, String date, String weight) {
        String scheduleField = scheduleId == null ? "" : "\"scheduleId\": \"%s\",".formatted(scheduleId);
        return """
                {
                  %s
                  "associationId": "%s",
                  "collectionDate": "%s",
                  "weightKg": %s
                }
                """.formatted(scheduleField, associationId, date, weight);
    }

    @Test
    void creatingARecordLinkedToAScheduleSucceeds() {
        String neighborId = createNeighbor("Ana Torres IT");
        String scheduleId = createSchedule(neighborId, "MONDAY", "09:00:00");
        String associationId = UUID.randomUUID().toString();

        String id = given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(scheduleId, associationId, "2026-01-05", "12.5"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201)
                .body("scheduleId", equalTo(scheduleId))
                .body("associationId", equalTo(associationId))
                .body("weightKg", equalTo(12.5f))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{neighborId}/collection-records/{id}", neighborId, id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id));
    }

    @Test
    void creatingARecordWritesAPendingOutboxEntry() throws com.fasterxml.jackson.core.JsonProcessingException {
        String neighborId = createNeighbor("Ana Torres IT Outbox");
        String associationId = UUID.randomUUID().toString();

        String id = given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, associationId, "2026-01-05", "5"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        List<OutboxEntry> pending = outboxRepository.findPendingBatch(50);
        OutboxEntry entry = pending.stream()
                .filter(e -> e.payloadJson().contains(id))
                .findFirst()
                .orElseThrow();
        assertThat(entry.routingKey()).isEqualTo("collection.record.registered");
        assertThat(entry.status()).isEqualTo(OutboxStatus.NEW);

        // Real evidence, not assumption: parse the actual JSON this app's real
        // ObjectMapper bean produced and assert its exact key set. Jackson 2.12+
        // serializes a record by its canonical components only -- routingKey()
        // is a plain override method, not a component, so it must NOT appear
        // here. Confirmed empirically (see CollectionRegisteredEvent's own
        // Javadoc). If it ever does (a Jackson upgrade, a config change), this
        // fails loudly instead of silently shipping an extra field a consumer
        // would reject under FAIL_ON_UNKNOWN_PROPERTIES.
        Map<String, Object> payloadFields = objectMapper.readValue(entry.payloadJson(), new TypeReference<>() {});
        assertThat(payloadFields.keySet())
                .containsExactlyInAnyOrder(
                        "eventId", "occurredAt", "recordId", "neighborId", "associationId", "collectionDate",
                        "weightKg");
    }

    @Test
    void creatingAnAdHocRecordWithoutAScheduleSucceeds() {
        String neighborId = createNeighbor("Ana Torres IT 2");
        String associationId = UUID.randomUUID().toString();

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, associationId, "2026-01-05", "8"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201)
                .body("scheduleId", nullValue());
    }

    @Test
    void creatingARecordWithARandomUnvalidatedAssociationIdSucceeds() {
        String neighborId = createNeighbor("Ana Torres IT 3");
        String randomAssociationId = UUID.randomUUID().toString();

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, randomAssociationId, "2026-01-05", "3"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201)
                .body("associationId", equalTo(randomAssociationId));
    }

    @Test
    void creatingARecordForABlockedAssociationReturns409() {
        String neighborId = createNeighbor("Ana Torres IT Blocked");
        UUID associationId = UUID.randomUUID();
        blockedAssociationRepository.block(BlockedAssociation.block(associationId, Instant.now()));

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, associationId.toString(), "2026-01-05", "5"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(409)
                .body("code", equalTo("COL-009"));
    }

    @Test
    void creatingUnderANonexistentNeighborReturnsNotFound() {
        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, UUID.randomUUID().toString(), "2026-01-05", "3"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-001"));
    }

    @Test
    void creatingWithAnotherNeighborsScheduleIdReturnsNotFound() {
        String neighborAId = createNeighbor("Ana Torres IT 10");
        String neighborBId = createNeighbor("Ana Torres IT 11");
        String scheduleBId = createSchedule(neighborBId, "MONDAY", "09:00:00");

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(scheduleBId, UUID.randomUUID().toString(), "2026-01-05", "3"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborAId)
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-006"));
    }

    @Test
    void listingReturnsRecordsForTheNeighbor() {
        String neighborId = createNeighbor("Ana Torres IT 4");
        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, UUID.randomUUID().toString(), "2026-01-05", "3"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(201);

        given()
                .when()
                .get("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void gettingAMissingRecordReturnsNotFound() {
        String neighborId = createNeighbor("Ana Torres IT 5");

        given()
                .when()
                .get(
                        "/neighbors/{neighborId}/collection-records/{id}",
                        neighborId,
                        "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-007"));
    }

    @Test
    void gettingARecordThroughAnotherNeighborsPathReturnsNotFound() {
        String neighborAId = createNeighbor("Ana Torres IT 6");
        String neighborBId = createNeighbor("Ana Torres IT 7");

        String recordBId = given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, UUID.randomUUID().toString(), "2026-01-05", "3"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborBId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{neighborId}/collection-records/{id}", neighborAId, recordBId)
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-007"));
    }

    @Test
    void creatingWithoutAnAssociationIdReturnsAValidationError() {
        String neighborId = createNeighbor("Ana Torres IT 8");
        String requestWithoutAssociationId = """
                {
                  "collectionDate": "2026-01-05",
                  "weightKg": 3
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithoutAssociationId)
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void creatingWithANonPositiveWeightReturnsAValidationError() {
        String neighborId = createNeighbor("Ana Torres IT 9");

        given()
                .contentType(ContentType.JSON)
                .body(createRecordRequest(null, UUID.randomUUID().toString(), "2026-01-05", "0"))
                .when()
                .post("/neighbors/{neighborId}/collection-records", neighborId)
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }
}

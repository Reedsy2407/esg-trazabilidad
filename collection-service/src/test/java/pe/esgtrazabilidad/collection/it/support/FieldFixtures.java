package pe.esgtrazabilidad.collection.it.support;

import static io.restassured.RestAssured.given;

import java.time.Instant;
import java.util.UUID;

import io.restassured.http.ContentType;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/**
 * Data the field ITs need, created through the real API with the staff token that
 * {@link RestAssuredSetup#authenticated(int)} installs by default. Each IT keeps its own
 * Postgres container; this only saves repeating the requests.
 */
public final class FieldFixtures {

    private FieldFixtures() {
    }

    public static String neighbor(String fullName, String districtUbigeo) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"fullName": "%s", "phone": "999000111", "address": "Calle Falsa 123", "districtUbigeo": "%s"}
                        """.formatted(fullName, districtUbigeo))
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    public static String schedule(String neighborId, String dayOfWeek, String time) {
        return given().contentType(ContentType.JSON)
                .body("""
                        {"dayOfWeek": "%s", "time": "%s"}
                        """.formatted(dayOfWeek, time))
                .post("/neighbors/{id}/schedules", neighborId)
                .then()
                .statusCode(201)
                .extract()
                .path("id");
    }

    public static void deactivateNeighbor(String neighborId) {
        given().contentType(ContentType.JSON)
                .body("{\"reason\": \"prueba\"}")
                .patch("/neighbors/{id}/deactivate", neighborId)
                .then()
                .statusCode(200);
    }

    /** Announces the recycler as the real consumer would (RecyclerStatusChanged), always newer than before. */
    public static void recyclerCopy(
            RecyclerStatusCopyRepository copies, String recyclerId, String associationId, RecyclerStatus status) {
        copies.applyIfNewer(UUID.fromString(recyclerId), UUID.fromString(associationId), status, Instant.now());
    }
}

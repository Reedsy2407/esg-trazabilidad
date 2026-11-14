package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.restassured.http.ContentType;
import pe.esgtrazabilidad.collection.it.support.FieldFixtures;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.recycler.domain.RecyclerStatus;
import pe.esgtrazabilidad.collection.recycler.port.out.RecyclerStatusCopyRepository;

/** CA-32: the recycler sees only what is assigned to them, reduced, with no phone anywhere. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class MyRouteApiIT {

    /** The neighbours' phone in FieldFixtures; it must never appear in a recycler's response. */
    private static final String NEIGHBOR_PHONE = "999000111";

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
    private RecyclerStatusCopyRepository copies;

    private final String association = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        RestAssuredSetup.authenticated(port);
    }

    private String recycler() {
        String id = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, id, association, RecyclerStatus.ACTIVE);
        return id;
    }

    private void assign(String recycler, String... schedules) {
        String ids = String.join(",", java.util.Arrays.stream(schedules).map(s -> "\"" + s + "\"").toList());
        given().contentType(ContentType.JSON)
                .body("{\"scheduleIds\": [" + ids + "]}")
                .put("/recyclers/{id}/assignment", recycler)
                .then()
                .statusCode(200);
    }

    private io.restassured.specification.RequestSpecification asRecycler(String recyclerId) {
        RestAssuredSetup.anonymous(port);
        return given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(recyclerId, association));
    }

    @Test
    void eachRecyclerSeesOnlyTheirOwnReducedRouteAndNeverAPhone() {
        String a = recycler();
        String b = recycler();
        String neighborA = FieldFixtures.neighbor("Vecina Ana RUTA", "150101");
        String neighborB = FieldFixtures.neighbor("Vecino Beto RUTA", "150101");
        String sa = FieldFixtures.schedule(neighborA, "MONDAY", "07:30:00");
        String sb = FieldFixtures.schedule(neighborB, "TUESDAY", "08:00:00");
        assign(a, sa);
        assign(b, sb);

        String bodyA = asRecycler(a).get("/me/route").then().statusCode(200)
                .body("$", hasSize(1))
                .body("[0].scheduleId", equalTo(sa))
                .body("[0].dayOfWeek", equalTo("MONDAY"))
                .body("[0].time", equalTo("07:30:00"))
                .body("[0].neighbor.id", equalTo(neighborA))
                .body("[0].neighbor.fullName", equalTo("Vecina Ana RUTA"))
                .body("[0].neighbor.address", equalTo("Calle Falsa 123"))
                .extract()
                .asString();
        String bodyB = asRecycler(b).get("/me/route").then().statusCode(200).body("[0].scheduleId", equalTo(sb)).extract().asString();

        // Absence assertions on the parsed structure and on the raw text.
        Map<String, Object> neighbor = asRecycler(a).get("/me/route").then().extract().path("[0].neighbor");
        assertThat(neighbor.keySet()).containsExactlyInAnyOrder("id", "fullName", "address", "district");
        assertThat(bodyA).doesNotContain("phone").doesNotContain(NEIGHBOR_PHONE).doesNotContain(sb).doesNotContain("Beto");
        assertThat(bodyB).doesNotContain("phone").doesNotContain(NEIGHBOR_PHONE).doesNotContain(sa).doesNotContain("Ana");
    }

    @Test
    void pausedSchedulesAndInactiveNeighboursAreNotOnTheRoute() {
        String recycler = recycler();
        String active = FieldFixtures.neighbor("Activa RUTA2", "150103");
        String keep = FieldFixtures.schedule(active, "MONDAY", "09:00:00");
        String pause = FieldFixtures.schedule(active, "TUESDAY", "09:00:00");
        String inactive = FieldFixtures.neighbor("Inactiva RUTA2", "150103");
        String away = FieldFixtures.schedule(inactive, "MONDAY", "10:00:00");
        assign(recycler, keep, pause, away);
        given().patch("/neighbors/{n}/schedules/{s}/pause", active, pause).then().statusCode(200);
        FieldFixtures.deactivateNeighbor(inactive);

        asRecycler(recycler).get("/me/route").then().statusCode(200).body("scheduleId", contains(keep));
    }

    @Test
    void aRecyclerWithNothingAssignedGetsAnEmptyList() {
        asRecycler(recycler()).get("/me/route").then().statusCode(200).body("$", empty());
    }

    @Test
    void aRouteOfAnotherAssociationIsNotShown() {
        String r = recycler();
        String schedule = FieldFixtures.schedule(FieldFixtures.neighbor("Otra RUTA3", "150104"), "MONDAY", "11:00:00");
        assign(r, schedule);
        // Same recycler id, but a token of another association: the assignment's association differs.
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(r, UUID.randomUUID().toString()))
                .get("/me/route")
                .then()
                .statusCode(200)
                .body("$", empty());
    }

    @Test
    void staffAndMissingTokensCannotUseTheFieldRoute() {
        given().get("/me/route").then().statusCode(403).body("code", equalTo("AUTH-000"));
        RestAssuredSetup.anonymous(port);
        given().get("/me/route").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void withoutConsentTheRouteIsAuth019() {
        String r = recycler();
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken(r, association, null, false))
                .get("/me/route")
                .then()
                .statusCode(409)
                .body("code", equalTo("AUTH-019"));
    }

    @Test
    void anInactiveRecyclerOrOneWithoutCopyIsCol019() {
        String inactive = UUID.randomUUID().toString();
        FieldFixtures.recyclerCopy(copies, inactive, association, RecyclerStatus.INACTIVE);
        String unknown = UUID.randomUUID().toString();
        for (String r : List.of(inactive, unknown)) {
            asRecycler(r).get("/me/route").then().statusCode(403).body("code", equalTo("COL-019"));
        }
    }
}

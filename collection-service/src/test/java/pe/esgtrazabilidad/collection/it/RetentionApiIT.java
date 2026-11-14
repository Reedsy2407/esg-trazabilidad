package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

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

import io.restassured.http.ContentType;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

/** POST /retention/run: ADMIN only, answers the terms in force and the counts. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class RetentionApiIT {

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
    void setUp() {
        RestAssuredSetup.anonymous(port);
    }

    @Test
    void anAdminRunsItAndGetsTheTermsInForceAndTheCounts() {
        given().header("Authorization", "Bearer " + TestJwtTokens.adminToken())
                .contentType(ContentType.JSON)
                .post("/retention/run")
                .then()
                .statusCode(200)
                .body("gpsDays", equalTo(90))
                .body("photoMonths", equalTo(12))
                .body("workdaysSummarized", greaterThanOrEqualTo(0))
                .body("pointsDeleted", greaterThanOrEqualTo(0))
                .body("photosDeleted", greaterThanOrEqualTo(0))
                .body("photosFailed", greaterThanOrEqualTo(0));
    }

    @Test
    void staffAndTheFieldAppGetAuth000AndNothingIsDeleted() {
        given().header("Authorization", "Bearer " + TestJwtTokens.validToken())
                .post("/retention/run").then().statusCode(403).body("code", equalTo("AUTH-000"));
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken())
                .post("/retention/run").then().statusCode(403).body("code", equalTo("AUTH-000"));
    }

    @Test
    void withoutATokenTheAnswerIs401() {
        given().post("/retention/run").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }
}

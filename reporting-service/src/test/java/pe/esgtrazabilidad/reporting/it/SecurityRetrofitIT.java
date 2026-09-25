package pe.esgtrazabilidad.reporting.it;


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
import static org.hamcrest.Matchers.equalTo;

/**
 * The two required negative checks for this service's retrofit (per
 * SPEC-auth-service.md's Testing Strategy): no token, and a token signed
 * with the wrong secret, both against a real, already-existing business
 * endpoint. Deliberately its own small class, not folded into
 * TrackedCompanyApiIT/etc. -- unlike those classes, this one must NOT set a
 * default valid-token request spec in @BeforeEach.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class SecurityRetrofitIT {

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
        RestAssuredSetup.anonymous(port);
    }

    @Test
    void aBusinessEndpointWithNoTokenReturns401() {
        given().when().get("/tracked-companies").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void aBusinessEndpointWithATokenSignedByTheWrongSecretReturns401() {
        String wrongToken = TestJwtTokens.signToken("a-totally-different-wrong-secret-value-here-123", "someone");

        given().header("Authorization", "Bearer " + wrongToken)
                .when()
                .get("/tracked-companies")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-000"));
    }

    @Test
    void swaggerUiRemainsReachableWithoutAToken() {
        given().redirects().follow(false).when().get("/swagger-ui/index.html").then().statusCode(200);
    }

    @Test
    void theSwaggerUiEntryPointRedirectsWithoutAToken() {
        given().redirects().follow(false).when().get("/swagger-ui.html").then().statusCode(302);
    }
}

package pe.esgtrazabilidad.auth.it;

import io.restassured.RestAssured;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.it.support.OpenApiExport;

import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * The generated OpenAPI document for auth-service's own controllers, checked
 * over real HTTP with no token (the docs are public by design, see
 * SPEC-auth-service.md's Resolved Decisions) -- the automated form of the
 * manual Swagger checks the other services' springdoc tasks did.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocsIT {

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

    @Test
    void theApiDocsListEveryAuthEndpointWithoutAToken() {
        given().when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .body("paths.'/auth/login'.post", notNullValue())
                .body("paths.'/auth/me'.get", notNullValue())
                .body("paths.'/auth/staff-users'.post", notNullValue());
    }

    @Test
    void creatingAStaffUserIsDocumentedAs201() {
        given().when()
                .get("/v3/api-docs")
                .then()
                .body("paths.'/auth/staff-users'.post.responses", hasKey("201"));
    }

    @Test
    void theJwtPrincipalOfMeIsNotDocumentedAsAClientParameter() {
        // `me(@AuthenticationPrincipal Jwt jwt)`: the principal comes from the
        // bearer token, so the docs must not ask the client to send a `jwt`.
        given().when()
                .get("/v3/api-docs")
                .then()
                .body("paths.'/auth/me'.get.parameters", anyOf(nullValue(), empty()));
    }

    @Test
    void requestSchemasCarryTheirValidationConstraints() {
        given().when()
                .get("/v3/api-docs")
                .then()
                .body("components.schemas.LoginRequest.required", hasItems("email", "password"))
                .body("components.schemas.CreateStaffUserRequest.required", hasItems("email", "password", "fullName"));
    }

    @Test
    void theDocumentedSwaggerUiEntryPointIsReachableWithoutAToken() {
        // springdoc's documented entry point is /swagger-ui.html, which
        // redirects into /swagger-ui/index.html -- both must be public.
        given().redirects()
                .follow(false)
                .when()
                .get("/swagger-ui.html")
                .then()
                .statusCode(302);
        given().when().get("/swagger-ui/index.html").then().statusCode(200);
    }

    // Reuses this class's own Testcontainers/SpringBootTest startup instead
    // of a near-duplicate OpenApiExportIT, the way the other three services
    // export theirs (they have no equivalent pre-existing OpenApiDocsIT).
    // Two independent fetches, normalized separately: proves the export is
    // deterministic (same bytes every run), not just that it happened once.
    @Test
    void exportsADeterministicOpenApiDocument() {
        String normalizedFirst = OpenApiExport.normalize(fetchApiDocs());
        String normalizedSecond = OpenApiExport.normalize(fetchApiDocs());

        assertThat(normalizedSecond).isEqualTo(normalizedFirst);

        OpenApiExport.writeTo(Path.of("target", "openapi", "auth-service.json"), normalizedFirst);
    }

    private String fetchApiDocs() {
        return given().when().get("/v3/api-docs").then().statusCode(200).extract().asString();
    }
}

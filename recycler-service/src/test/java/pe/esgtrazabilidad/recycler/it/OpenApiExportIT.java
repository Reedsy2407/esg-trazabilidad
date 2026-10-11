package pe.esgtrazabilidad.recycler.it;

import io.restassured.http.ContentType;

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

import pe.esgtrazabilidad.recycler.it.support.OpenApiExport;
import pe.esgtrazabilidad.recycler.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.recycler.it.support.TestJwtTokens;

import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exports recycler-service's own OpenAPI document to target/openapi/, the
 * same deterministic JSON portal/scripts/export-openapi.mjs later copies
 * into portal/Writerside/specs/. /v3/api-docs is public (see
 * SecurityConfig), so this never needs a token.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class OpenApiExportIT {

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

    // Two independent fetches, normalized separately: proves the export is
    // deterministic (same bytes every run), not just that it happened once.
    @Test
    void exportsADeterministicOpenApiDocument() {
        String normalizedFirst = OpenApiExport.normalize(fetchApiDocs());
        String normalizedSecond = OpenApiExport.normalize(fetchApiDocs());

        assertThat(normalizedSecond).isEqualTo(normalizedFirst);

        OpenApiExport.writeTo(Path.of("target", "openapi", "recycler-service.json"), normalizedFirst);
    }

    private String fetchApiDocs() {
        return given().when()
                .get("/v3/api-docs")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .extract()
                .asString();
    }
}

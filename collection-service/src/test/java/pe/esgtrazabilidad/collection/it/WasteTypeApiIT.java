package pe.esgtrazabilidad.collection.it;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

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

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;
import pe.esgtrazabilidad.collection.wastetype.WasteType;
import pe.esgtrazabilidad.collection.wastetype.WasteTypeCatalog;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/** CA-29: the catalogue of 8 types and the single COL-014 check. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {"JWT_SECRET=" + TestJwtTokens.TEST_SECRET, "esg.events.outbox-dispatcher.enabled=false"})
class WasteTypeApiIT {

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
    private WasteTypeCatalog catalog;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        RestAssuredSetup.authenticated(port);
        jdbc.update("update waste_type set active = true");
    }

    @Test
    void staffGetsTheEightSeededTypes() {
        given().when()
                .get("/waste-types")
                .then()
                .statusCode(200)
                .body("$", hasSize(8))
                .body("code", org.hamcrest.Matchers.containsInAnyOrder(
                        "PAPEL", "CARTON", "PET", "PLAST_OTRO", "VIDRIO", "METAL", "TETRABRIK", "TEXTIL"))
                .body("find { it.code == 'TEXTIL' }.name", equalTo("Textiles, caucho y cuero"));
    }

    @Test
    void theFieldAppReadsTheSameCatalogue() {
        RestAssuredSetup.anonymous(port);
        given().header("Authorization", "Bearer " + TestJwtTokens.recyclerToken())
                .when()
                .get("/waste-types")
                .then()
                .statusCode(200)
                .body("$", hasSize(8));
    }

    @Test
    void withoutATokenTheAnswerIsAuth000() {
        RestAssuredSetup.anonymous(port);
        given().when().get("/waste-types").then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    @Test
    void aRetiredTypeIsNotListedNorAccepted() {
        jdbc.update("update waste_type set active = false where code = 'PAPEL'");

        given().when().get("/waste-types").then().statusCode(200).body("$", hasSize(7));
        assertThat(catalog.listActive()).extracting(WasteType::getCode).doesNotContain("PAPEL");
        assertThatThrownBy(() -> catalog.requireActive("PAPEL"))
                .isInstanceOfSatisfying(ApplicationException.class, e -> assertThat(e.getError())
                        .isEqualTo(CollectionErrors.INVALID_WASTE_TYPE));
    }

    @Test
    void aCodeOutsideTheCatalogueIsCol014() {
        for (String code : new String[] {"ORGANICO", "pet", "", null}) {
            assertThatThrownBy(() -> catalog.requireActive(code))
                    .isInstanceOfSatisfying(ApplicationException.class, e -> {
                        assertThat(e.getError().getCode()).isEqualTo("COL-014");
                        assertThat(e.getError().getStatus().value()).isEqualTo(400);
                    });
        }
        catalog.requireActive("PET");
    }

    @Test
    void theListIsSortedByName() {
        given().when().get("/waste-types").then().statusCode(200).body("name", org.hamcrest.Matchers.hasItem("Cartón"));
        assertThat(catalog.listActive()).extracting(WasteType::getName).isSorted();
    }
}

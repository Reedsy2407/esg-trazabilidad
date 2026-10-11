package pe.esgtrazabilidad.collection.it;

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

import pe.esgtrazabilidad.collection.it.support.RestAssuredSetup;
import pe.esgtrazabilidad.collection.it.support.TestJwtTokens;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + TestJwtTokens.TEST_SECRET)
class NeighborApiIT {

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
        RestAssuredSetup.authenticated(port);
    }

    private String createNeighborRequest(String fullName) {
        return """
                {
                  "fullName": "%s",
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "districtUbigeo": "150140"
                }
                """.formatted(fullName);
    }

    @Test
    void createGetAndListANeighbor() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest("Ana Torres IT"))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .body("fullName", equalTo("Ana Torres IT"))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract()
                .path("id");

        given()
                .when()
                .get("/neighbors/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("fullName", equalTo("Ana Torres IT"));

        given()
                .when()
                .get("/neighbors")
                .then()
                .statusCode(200)
                .body("content", hasSize(greaterThanOrEqualTo(1)));
    }

    @Test
    void gettingAMissingNeighborReturnsNotFound() {
        given()
                .when()
                .get("/neighbors/{id}", "00000000-0000-0000-0000-000000000000")
                .then()
                .statusCode(404)
                .body("code", equalTo("COL-001"));
    }

    @Test
    void creatingWithoutAnAddressReturnsAValidationError() {
        String requestWithoutAddress = """
                {
                  "fullName": "Ana Torres IT",
                  "phone": "999999999",
                  "districtUbigeo": "150140"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithoutAddress)
                .when()
                .post("/neighbors")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void creatingWithoutAFullNameReturnsAValidationError() {
        String requestWithoutFullName = """
                {
                  "phone": "999999999",
                  "address": "Av. Siempre Viva 123",
                  "districtUbigeo": "150140"
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(requestWithoutFullName)
                .when()
                .post("/neighbors")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
    }

    @Test
    void listingWithAnOversizedPageSizeIsCappedAtTheConfiguredMaximum() {
        given()
                .queryParam("size", 99999)
                .when()
                .get("/neighbors")
                .then()
                .statusCode(200)
                .body("size", equalTo(100));
    }

    @Test
    void creatingWithAUbigeoAnswersTheCatalogNameAndTheCode() {
        given()
                .contentType(ContentType.JSON)
                .body(requestWithDistrict("Rosa Rímac IT", "\"districtUbigeo\": \"150128\""))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .body("districtUbigeo", equalTo("150128"))
                .body("district", equalTo("Rímac"));
    }

    @Test
    void theV2FreeTextDistrictStillWorksWhenItNamesACatalogDistrict() {
        given()
                .contentType(ContentType.JSON)
                .body(requestWithDistrict("Luis Comas IT", "\"district\": \"comas\""))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .body("districtUbigeo", equalTo("150110"))
                .body("district", equalTo("Comas"));
    }

    @Test
    void aDistrictOutsideLimaMetropolitanaAndCallaoOrNoDistrictIsCol010() {
        for (String district : new String[] {
                "\"districtUbigeo\": \"150201\"", "\"district\": \"Surco\"", "\"phone\": \"1\""}) {
            given()
                    .contentType(ContentType.JSON)
                    .body(requestWithDistrict("Sin Distrito IT", district))
                    .when()
                    .post("/neighbors")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("COL-010"));
        }
    }

    @Test
    void filtersByUbigeoAndByTheV2NameIgnoringCaseAndAccents() {
        given()
                .contentType(ContentType.JSON)
                .body(requestWithDistrict("Filtro Breña IT", "\"districtUbigeo\": \"150105\""))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201);

        given().queryParam("districtUbigeo", "150105").when().get("/neighbors").then()
                .statusCode(200)
                .body("content.fullName", hasItem("Filtro Breña IT"))
                .body("content.districtUbigeo", everyItem(equalTo("150105")));
        given().queryParam("district", "  BRENA ").when().get("/neighbors").then()
                .statusCode(200)
                .body("content.fullName", hasItem("Filtro Breña IT"));
        given().queryParam("district", "Surco").when().get("/neighbors").then()
                .statusCode(200)
                .body("content", hasSize(0));
        given().queryParam("districtUbigeo", "999999").when().get("/neighbors").then()
                .statusCode(400)
                .body("code", equalTo("COL-010"));
    }

    private String requestWithDistrict(String fullName, String districtField) {
        return """
                {
                  "fullName": "%s",
                  "address": "Jr. Prueba 1",
                  %s
                }
                """.formatted(fullName, districtField);
    }

    @Test
    void editingANeighborReplacesItsDataAndPersistsIt() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(createNeighborRequest("Editar IT"))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "fullName": "Editado IT",
                          "address": "Jr. Editado 9",
                          "districtUbigeo": "070102"
                        }
                        """)
                .when()
                .patch("/neighbors/{id}", id)
                .then()
                .statusCode(200)
                .body("fullName", equalTo("Editado IT"))
                .body("phone", nullValue())
                .body("district", equalTo("Bellavista"));

        given().when().get("/neighbors/{id}", id).then()
                .statusCode(200)
                .body("fullName", equalTo("Editado IT"))
                .body("address", equalTo("Jr. Editado 9"))
                .body("districtUbigeo", equalTo("070102"))
                .body("phone", nullValue())
                .body("status", equalTo("ACTIVE"));
    }

    @Test
    void editingWithAMissingFieldADistrictOutsideTheListOrAMissingNeighborIsRefused() {
        String id = given()
                .contentType(ContentType.JSON)
                .body(requestWithDistrict("Editar Mal IT", "\"districtUbigeo\": \"150140\""))
                .when()
                .post("/neighbors")
                .then()
                .statusCode(201)
                .extract()
                .path("id");

        given().contentType(ContentType.JSON)
                .body("{\"fullName\": \"X\", \"address\": \"Jr. 1\"}")
                .when().patch("/neighbors/{id}", id).then()
                .statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        given().contentType(ContentType.JSON)
                .body("{\"fullName\": \"X\", \"address\": \"Jr. 1\", \"districtUbigeo\": \"150201\"}")
                .when().patch("/neighbors/{id}", id).then()
                .statusCode(400).body("code", equalTo("COL-010"));
        given().contentType(ContentType.JSON)
                .body("{\"fullName\": \"X\", \"address\": \"Jr. 1\", \"districtUbigeo\": \"150112\"}")
                .when().patch("/neighbors/{id}", "00000000-0000-0000-0000-000000000000").then()
                .statusCode(404).body("code", equalTo("COL-001"));
    }
}

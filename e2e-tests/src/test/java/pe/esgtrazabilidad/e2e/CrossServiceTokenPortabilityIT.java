package pe.esgtrazabilidad.e2e;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.e2e.support.ServiceProcess;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * SPEC-auth-service.md's cross-service token portability requirement: a token
 * issued by a real auth-service login works, unmodified, against real running
 * recycler-service, collection-service and reporting-service instances that
 * only share the JWT_SECRET value -- no gateway, and no synchronous call from
 * any of them back to auth-service.
 *
 * All four services run as their own packaged jars in separate OS processes
 * (see ServiceProcess), against one Testcontainers Postgres (a database per
 * service) and one RabbitMQ. The credentials are not seeded: auth-service
 * starts with an empty staff_user table and ADMIN_BOOTSTRAP_EMAIL set, and the
 * test logs in with the password the bootstrap runner generates and logs, the
 * same way an operator would on a first deploy.
 *
 * auth-service is shut down before the token is used anywhere else, so the
 * other three accepting it proves validation is offline -- if any of them
 * called auth-service to check a token, that call would now fail.
 */
@Testcontainers
class CrossServiceTokenPortabilityIT {

    private static final String SHARED_JWT_SECRET = "e2e-shared-test-only-jwt-secret-at-least-32-bytes";
    private static final String BOOTSTRAP_EMAIL = "admin@e2e.test";
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

    private static ServiceProcess auth;
    private static ServiceProcess recycler;
    private static ServiceProcess collection;
    private static ServiceProcess reporting;

    @BeforeAll
    static void startServices() throws Exception {
        auth = launch("auth-service", Map.of("ADMIN_BOOTSTRAP_EMAIL", BOOTSTRAP_EMAIL));
        recycler = launch("recycler-service", Map.of());
        collection = launch("collection-service", Map.of());
        reporting = launch("reporting-service", Map.of());

        // Launched together above, awaited one by one here: startup overlaps.
        for (ServiceProcess service : List.of(auth, recycler, collection, reporting)) {
            service.awaitStarted(STARTUP_TIMEOUT);
        }
    }

    @AfterAll
    static void stopServices() throws Exception {
        for (ServiceProcess service : new ServiceProcess[] {auth, recycler, collection, reporting}) {
            if (service != null) {
                service.stop();
            }
        }
    }

    @Test
    void aTokenFromARealAuthServiceLoginWorksUnmodifiedOnTheOtherThreeServicesWithAuthServiceDown()
            throws Exception {
        Matcher bootstrap = auth.findInLog("email=(\\S+) password=(\\S+)");
        assertThat(bootstrap.group(1)).isEqualTo(BOOTSTRAP_EMAIL);

        String token = on(auth)
                .contentType(ContentType.JSON)
                .body("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(BOOTSTRAP_EMAIL, bootstrap.group(2)))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        // The token is auth-service's own, for the bootstrap account.
        on(auth).header("Authorization", "Bearer " + token)
                .when()
                .get("/auth/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(BOOTSTRAP_EMAIL));

        auth.stop();
        assertThat(auth.isAlive()).isFalse();

        assertAcceptsTokenAndRejectsNone(recycler, "/associations", token);
        assertAcceptsTokenAndRejectsNone(collection, "/companies", token);
        assertAcceptsTokenAndRejectsNone(reporting, "/tracked-companies", token);
    }

    /**
     * The 401 without a token is what makes the 200 meaningful: it shows the
     * endpoint is protected, so the token -- not an open endpoint -- is what
     * let the first request through.
     */
    private static void assertAcceptsTokenAndRejectsNone(ServiceProcess service, String path, String token) {
        on(service).header("Authorization", "Bearer " + token).when().get(path).then().statusCode(200);

        on(service).when().get(path).then().statusCode(401).body("code", equalTo("AUTH-000"));
    }

    private static RequestSpecification on(ServiceProcess service) {
        // Explicit per-request base URI and port: no RestAssured static state
        // (see recycler-service's RestAssuredSetup for why that matters).
        return given().baseUri("http://localhost").port(service.port());
    }

    private static ServiceProcess launch(String name, Map<String, String> extraEnv) throws Exception {
        String database = name.replace("-service", "");
        postgres.execInContainer("psql", "-U", postgres.getUsername(), "-d", postgres.getDatabaseName(),
                "-c", "CREATE DATABASE " + database);

        Map<String, String> env = new HashMap<>(extraEnv);
        env.put("DB_URL", "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getFirstMappedPort() + "/" + database);
        env.put("DB_USERNAME", postgres.getUsername());
        env.put("DB_PASSWORD", postgres.getPassword());
        env.put("RABBITMQ_HOST", rabbitmq.getHost());
        env.put("RABBITMQ_PORT", String.valueOf(rabbitmq.getAmqpPort()));
        env.put("RABBITMQ_USER", rabbitmq.getAdminUsername());
        env.put("RABBITMQ_PASSWORD", rabbitmq.getAdminPassword());
        env.put("JWT_SECRET", SHARED_JWT_SECRET);
        return ServiceProcess.launch(name, env);
    }
}

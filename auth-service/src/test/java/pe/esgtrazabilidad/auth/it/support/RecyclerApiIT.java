package pe.esgtrazabilidad.auth.it.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static io.restassured.RestAssured.given;

/**
 * Base of the recycler-account ITs: one Postgres for the whole JVM (a single Spring context is reused
 * by every subclass), a clock the test moves, real staff logins, and the status copy fed the way the
 * consumer feeds it. Each test uses fresh UUIDs, so tests do not depend on each other's rows.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=" + RecyclerApiIT.JWT_SECRET)
@Import(RecyclerApiIT.TestClockConfig.class)
public abstract class RecyclerApiIT {

    protected static final String JWT_SECRET = "recycler-api-it-test-secret-at-least-32-bytes!!";

    /** Real "now": the JWT decoder checks expiry against the real clock, so tokens must be valid in real time. */
    protected static final Instant START = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @LocalServerPort
    private int port;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected RecyclerStatusCopyRepository statusCopies;

    @Autowired
    private StaffUserRepository staffUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void pointRestAssuredAtThisContext() {
        RestAssured.port = port;
        clock.set(START);
    }

    /** What the RecyclerStatusChanged consumer would have written. */
    protected void seedCopy(UUID recyclerId, UUID associationId, RecyclerStatus status) {
        statusCopies.applyIfNewer(recyclerId, associationId, status, Instant.now());
    }

    protected void setCopyStatus(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt) {
        statusCopies.applyIfNewer(recyclerId, associationId, status, occurredAt);
    }

    /** A real login as staff, not a hand-made token. */
    protected String staffToken(StaffRole role) {
        String email = "personal" + SEQUENCE.incrementAndGet() + "-" + UUID.randomUUID() + "@esgtrazabilidad.pe";
        String password = "clave-de-prueba-" + UUID.randomUUID();
        staffUsers.save(StaffUser.create(email, passwordEncoder.encode(password), "Personal de prueba", role, false));
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    /** A recycler with an ACTIVE status copy and a code issued by staff through the real API. */
    public record IssuedAccount(UUID recyclerId, UUID associationId, UUID accountId, String code) {}

    protected IssuedAccount issueAccount() {
        UUID recyclerId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();
        seedCopy(recyclerId, associationId, RecyclerStatus.ACTIVE);
        io.restassured.response.Response created = as(staffToken(StaffRole.STAFF))
                .body(Map.of("recyclerId", recyclerId.toString()))
                .post("/auth/recycler-accounts");
        created.then().statusCode(201);
        return new IssuedAccount(
                recyclerId, associationId, UUID.fromString(created.path("accountId")), created.path("activationCode"));
    }

    /** POST /auth/recycler/activate from the given client address (resolved from X-Forwarded-For). */
    protected io.restassured.response.Response activate(String code, String installId, String clientAddress) {
        return given().contentType("application/json")
                .header("X-Forwarded-For", clientAddress)
                .body(Map.of("code", code, "deviceLabel", "Honor X7b de prueba", "deviceInstallId", installId))
                .post("/auth/recycler/activate");
    }

    /** A phone with a live session, as the app would hold it right after activating. */
    public record Session(
            UUID accountId, UUID recyclerId, UUID associationId, UUID deviceId, String accessToken, String refreshToken) {}

    protected Session activateSession() {
        IssuedAccount issued = issueAccount();
        io.restassured.response.Response response = activate(issued.code(), "install-" + UUID.randomUUID(), uniqueAddress());
        response.then().statusCode(200);
        UUID deviceId = jdbc.queryForObject(
                "select id from recycler_device where account_id = ?", UUID.class, issued.accountId());
        return new Session(
                issued.accountId(),
                issued.recyclerId(),
                issued.associationId(),
                deviceId,
                response.path("accessToken"),
                response.path("refreshToken"));
    }

    protected io.restassured.response.Response refresh(String refreshToken) {
        return given().contentType("application/json")
                .body(Map.of("refreshToken", refreshToken))
                .post("/auth/recycler/refresh");
    }

    protected static String uniqueAddress() {
        int n = SEQUENCE.incrementAndGet();
        return "10." + (n / 65536 % 256) + "." + (n / 256 % 256) + "." + (n % 256);
    }

    /** A well-formed code nobody issued. */
    protected static String unissuedCode() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.Random random = new java.security.SecureRandom();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    /** A RECYCLER-role token signed like auth-service's own, for "wrong role" checks. */
    protected static String recyclerToken(UUID accountId, UUID recyclerId, UUID associationId, UUID deviceId) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(accountId.toString())
                    .claim("role", "RECYCLER")
                    .claim("rid", recyclerId.toString())
                    .claim("aid", associationId.toString())
                    .claim("did", deviceId.toString())
                    .expirationTime(java.util.Date.from(Instant.now().plusSeconds(900)))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException(exception);
        }
    }

    protected static RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType("application/json");
    }
}

package pe.esgtrazabilidad.auth.it;

import java.text.ParseException;
import java.util.Map;

import com.nimbusds.jwt.SignedJWT;

import io.restassured.RestAssured;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=staff-user-api-it-test-secret-32-bytes-min!!")
class StaffUserApiIT {

    private static final String STRONG = "una-clave-larga-y-segura";

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
    private StaffUserRepository staffUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUpRestAssured() {
        RestAssured.port = port;
    }

    private StaffUser save(String email, String rawPassword, StaffRole role) {
        return staffUserRepository.save(
                StaffUser.create(email, passwordEncoder.encode(rawPassword), "Persona " + email, role, false));
    }

    /** A real login, not a hand-minted token -- proves the actual issued-token/protected-endpoint path together. */
    private String login(String email, String rawPassword) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", rawPassword))
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");
    }

    private String adminToken(String email) {
        save(email, "password-del-admin-123", StaffRole.ADMIN);
        return login(email, "password-del-admin-123");
    }

    private String staffToken(String email) {
        save(email, "password-del-staff-123", StaffRole.STAFF);
        return login(email, "password-del-staff-123");
    }

    private static io.restassured.specification.RequestSpecification as(String token) {
        return given().header("Authorization", "Bearer " + token).contentType("application/json");
    }

    @Test
    void everyAdminRouteRejectsARequestWithoutATokenWith401() {
        given().get("/auth/staff-users").then().statusCode(401);
        given().contentType("application/json")
                .body(Map.of("email", "n@esgtrazabilidad.pe", "password", STRONG, "fullName", "N"))
                .post("/auth/staff-users")
                .then()
                .statusCode(401);
    }

    @Test
    void aStaffMemberGets403Auth006OnEveryAdminRoute() {
        String token = staffToken("staff1@esgtrazabilidad.pe");
        StaffUser other = save("objetivo1@esgtrazabilidad.pe", "password-objetivo-123", StaffRole.STAFF);

        as(token).get("/auth/staff-users").then().statusCode(403).body("code", equalTo("AUTH-006"));
        as(token)
                .body(Map.of("email", "n1@esgtrazabilidad.pe", "password", STRONG, "fullName", "N"))
                .post("/auth/staff-users")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-006"));
        as(token)
                .body(Map.of("reason", "x"))
                .patch("/auth/staff-users/" + other.getId() + "/deactivate")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-006"));
        as(token)
                .body(Map.of("reason", "x"))
                .patch("/auth/staff-users/" + other.getId() + "/reactivate")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-006"));
        as(token)
                .body(Map.of("newPassword", STRONG))
                .post("/auth/staff-users/" + other.getId() + "/password")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-006"));
        assertThat(staffUserRepository.findById(other.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void aStaffMemberStillUsesTheRestOfTheirAccountAndTheTokenCarriesTheRole() throws ParseException {
        String token = staffToken("staff2@esgtrazabilidad.pe");

        as(token).get("/auth/me").then().statusCode(200).body("role", equalTo("STAFF")).body("email", equalTo("staff2@esgtrazabilidad.pe"));
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getStringClaim("role")).isEqualTo("STAFF");
        assertThat(SignedJWT.parse(token).getJWTClaimsSet().getClaim("pwdChange")).isNull();
    }

    @Test
    void anAdminCreatesAStaffAccountThatMustChangeItsPassword() throws ParseException {
        String token = adminToken("admin1@esgtrazabilidad.pe");

        as(token)
                .body(Map.of("email", "Nueva@esgtrazabilidad.pe", "password", STRONG, "fullName", "Persona Nueva"))
                .post("/auth/staff-users")
                .then()
                .statusCode(201)
                .body("email", equalTo("nueva@esgtrazabilidad.pe"))
                .body("role", equalTo("STAFF"))
                .body("active", equalTo(true))
                .body("mustChangePassword", equalTo(true))
                .body("passwordChangedAt", nullValue())
                .body("id", notNullValue());

        String newToken = login("nueva@esgtrazabilidad.pe", STRONG);
        assertThat(SignedJWT.parse(newToken).getJWTClaimsSet().getBooleanClaim("pwdChange")).isTrue();
    }

    @Test
    void creatingARejectedAccountReturnsTheRightCodeAndSavesNothing() {
        String token = adminToken("admin2@esgtrazabilidad.pe");
        save("duplicada@esgtrazabilidad.pe", "password-duplicada-123", StaffRole.STAFF);

        as(token)
                .body(Map.of("email", "sinpunto@esgtrazabilidad", "password", STRONG, "fullName", "X"))
                .post("/auth/staff-users")
                .then()
                .statusCode(400)
                .body("code", equalTo("AUTH-005"));
        as(token)
                .body(Map.of("email", "debil@esgtrazabilidad.pe", "password", "corta", "fullName", "X"))
                .post("/auth/staff-users")
                .then()
                .statusCode(400)
                .body("code", equalTo("AUTH-007"));
        as(token)
                .body(Map.of("email", "duplicada@esgtrazabilidad.pe", "password", STRONG, "fullName", "X"))
                .post("/auth/staff-users")
                .then()
                .statusCode(409)
                .body("code", equalTo("AUTH-002"));
        as(token)
                .body(Map.of("email", "rol@esgtrazabilidad.pe", "password", STRONG, "fullName", "X", "role", "SUPERUSER"))
                .post("/auth/staff-users")
                .then()
                .statusCode(400);

        assertThat(staffUserRepository.findByEmail("sinpunto@esgtrazabilidad")).isEmpty();
        assertThat(staffUserRepository.findByEmail("debil@esgtrazabilidad.pe")).isEmpty();
    }

    @Test
    void theListShowsEveryAccountWithWhoLastChangedItsStatus() {
        String token = adminToken("admin3@esgtrazabilidad.pe");
        StaffUser target = save("lista@esgtrazabilidad.pe", "password-lista-123", StaffRole.STAFF);
        as(token).body(Map.of("reason", "Dejó la asociación")).patch("/auth/staff-users/" + target.getId() + "/deactivate").then().statusCode(200);

        as(token)
                .get("/auth/staff-users")
                .then()
                .statusCode(200)
                .body("find { it.email == 'lista@esgtrazabilidad.pe' }.active", equalTo(false))
                .body("find { it.email == 'lista@esgtrazabilidad.pe' }.statusReason", equalTo("Dejó la asociación"))
                .body("find { it.email == 'lista@esgtrazabilidad.pe' }.statusChangedBy", equalTo("admin3@esgtrazabilidad.pe"))
                .body("find { it.email == 'admin3@esgtrazabilidad.pe' }.role", equalTo("ADMIN"))
                .body("find { it.email == 'admin3@esgtrazabilidad.pe' }.statusReason", nullValue());
    }

    @Test
    void deactivatingBlocksTheLoginAndReactivatingRestoresIt() {
        String token = adminToken("admin4@esgtrazabilidad.pe");
        StaffUser target = save("baja@esgtrazabilidad.pe", "password-baja-1234", StaffRole.STAFF);

        as(token).body(Map.of("reason", "x")).patch("/auth/staff-users/" + target.getId() + "/deactivate").then().statusCode(200).body("active", equalTo(false));
        given().contentType("application/json")
                .body(Map.of("email", "baja@esgtrazabilidad.pe", "password", "password-baja-1234"))
                .post("/auth/login")
                .then()
                .statusCode(401)
                .body("code", equalTo("AUTH-001"));
        as(token).body(Map.of("reason", "otra")).patch("/auth/staff-users/" + target.getId() + "/deactivate").then().statusCode(409).body("code", equalTo("AUTH-010"));

        as(token).body(Map.of("reason", "volvió")).patch("/auth/staff-users/" + target.getId() + "/reactivate").then().statusCode(200).body("active", equalTo(true));
        login("baja@esgtrazabilidad.pe", "password-baja-1234");
        as(token).body(Map.of("reason", "ya")).patch("/auth/staff-users/" + target.getId() + "/reactivate").then().statusCode(409).body("code", equalTo("AUTH-010"));
    }

    @Test
    void theReasonIsRequiredAndAnAdminCannotActOnTheirOwnAccount() {
        StaffUser admin = save("admin5@esgtrazabilidad.pe", "password-del-admin-123", StaffRole.ADMIN);
        String token = login("admin5@esgtrazabilidad.pe", "password-del-admin-123");
        StaffUser target = save("motivo@esgtrazabilidad.pe", "password-motivo-123", StaffRole.STAFF);

        as(token).body(Map.of("reason", " ")).patch("/auth/staff-users/" + target.getId() + "/deactivate").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        as(token).body(Map.of("reason", "x")).patch("/auth/staff-users/" + admin.getId() + "/deactivate").then().statusCode(409).body("code", equalTo("AUTH-009"));
        as(token).body(Map.of("newPassword", STRONG)).post("/auth/staff-users/" + admin.getId() + "/password").then().statusCode(409).body("code", equalTo("AUTH-009"));
        assertThat(staffUserRepository.findById(admin.getId()).orElseThrow().isActive()).isTrue();
        assertThat(staffUserRepository.existsActiveAdmin()).isTrue();
    }

    @Test
    void aDeactivatedAdminLosesTheAdminPowerAtOnceEvenWithAStillValidToken() {
        String otherAdmin = adminToken("admin6@esgtrazabilidad.pe");
        StaffUser victim = save("admin7@esgtrazabilidad.pe", "password-del-admin-123", StaffRole.ADMIN);
        String victimToken = login("admin7@esgtrazabilidad.pe", "password-del-admin-123");

        as(otherAdmin).body(Map.of("reason", "x")).patch("/auth/staff-users/" + victim.getId() + "/deactivate").then().statusCode(200);

        as(victimToken).get("/auth/staff-users").then().statusCode(403).body("code", equalTo("AUTH-006"));
    }

    @Test
    void anAdminResetLetsThePersonInWithTheNewPasswordUntilTheyChooseTheirOwn() throws ParseException {
        String token = adminToken("admin8@esgtrazabilidad.pe");
        StaffUser target = save("reinicio@esgtrazabilidad.pe", "password-vieja-1234", StaffRole.STAFF);

        as(token).body(Map.of("newPassword", "weak")).post("/auth/staff-users/" + target.getId() + "/password").then().statusCode(400).body("code", equalTo("AUTH-007"));
        as(token).body(Map.of("newPassword", STRONG)).post("/auth/staff-users/" + target.getId() + "/password").then().statusCode(204);

        given().contentType("application/json")
                .body(Map.of("email", "reinicio@esgtrazabilidad.pe", "password", "password-vieja-1234"))
                .post("/auth/login")
                .then()
                .statusCode(401);
        String temporary = login("reinicio@esgtrazabilidad.pe", STRONG);
        assertThat(SignedJWT.parse(temporary).getJWTClaimsSet().getBooleanClaim("pwdChange")).isTrue();
        as(temporary).get("/auth/me").then().statusCode(200).body("mustChangePassword", equalTo(true)).body("passwordChangedAt", notNullValue());

        as(temporary)
                .body(Map.of("currentPassword", STRONG, "newPassword", "mi-propia-clave-bien-larga"))
                .post("/auth/me/password")
                .then()
                .statusCode(204);

        String own = login("reinicio@esgtrazabilidad.pe", "mi-propia-clave-bien-larga");
        assertThat(SignedJWT.parse(own).getJWTClaimsSet().getClaim("pwdChange")).isNull();
        as(own).get("/auth/me").then().statusCode(200).body("mustChangePassword", equalTo(false));
    }

    @Test
    void changingYourOwnPasswordNeedsTheRightCurrentOneAndAStrongNewOne() {
        String token = staffToken("propia@esgtrazabilidad.pe");

        as(token).body(Map.of("currentPassword", "equivocada-equivocada", "newPassword", STRONG)).post("/auth/me/password").then().statusCode(400).body("code", equalTo("AUTH-008"));
        as(token).body(Map.of("currentPassword", "password-del-staff-123", "newPassword", "corta")).post("/auth/me/password").then().statusCode(400).body("code", equalTo("AUTH-007"));
        as(token).body(Map.of("currentPassword", "password-del-staff-123", "newPassword", "password-del-staff-123")).post("/auth/me/password").then().statusCode(400).body("code", equalTo("AUTH-007"));
        given().contentType("application/json").body(Map.of("currentPassword", "x", "newPassword", STRONG)).post("/auth/me/password").then().statusCode(401);

        as(token).body(Map.of("currentPassword", "password-del-staff-123", "newPassword", STRONG)).post("/auth/me/password").then().statusCode(204);
        login("propia@esgtrazabilidad.pe", STRONG);
        as(token).get("/auth/me").then().statusCode(200).body("passwordChangedAt", notNullValue());
    }

    @Test
    void twoAdminsDeactivatingEachOtherAtTheSameTimeNeverLeaveTheSystemWithoutAnActiveAdmin() throws Exception {
        StaffUser a = save("par-a@esgtrazabilidad.pe", "password-del-admin-123", StaffRole.ADMIN);
        StaffUser b = save("par-b@esgtrazabilidad.pe", "password-del-admin-123", StaffRole.ADMIN);
        String tokenA = login("par-a@esgtrazabilidad.pe", "password-del-admin-123");
        String tokenB = login("par-b@esgtrazabilidad.pe", "password-del-admin-123");
        java.util.concurrent.CountDownLatch go = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Integer> aOnB = () -> {
            go.await();
            return as(tokenA).body(Map.of("reason", "x")).patch("/auth/staff-users/" + b.getId() + "/deactivate").statusCode();
        };
        java.util.concurrent.Callable<Integer> bOnA = () -> {
            go.await();
            return as(tokenB).body(Map.of("reason", "x")).patch("/auth/staff-users/" + a.getId() + "/deactivate").statusCode();
        };
        java.util.concurrent.Future<Integer> fa = pool.submit(aOnB);
        java.util.concurrent.Future<Integer> fb = pool.submit(bOnA);
        go.countDown();
        int sa = fa.get(30, java.util.concurrent.TimeUnit.SECONDS);
        int sb = fb.get(30, java.util.concurrent.TimeUnit.SECONDS);
        pool.shutdown();

        // Exactly one wins; the other finds itself deactivated (403) and an admin is still active.
        assertThat(java.util.List.of(sa, sb)).containsExactlyInAnyOrder(200, 403);
        assertThat(staffUserRepository.findById(a.getId()).orElseThrow().isActive()
                        || staffUserRepository.findById(b.getId()).orElseThrow().isActive())
                .isTrue();
    }

    @Test
    void aTokenIssuedBeforeThePasswordChangeStillWorksUntilItExpiresTheDocumentedLimitation() {
        // Decision of Angel (2026-10-15): services validate the JWT without asking auth-service, so a
        // change does not revoke tokens already issued. This test pins that behaviour on purpose.
        String token = staffToken("limite@esgtrazabilidad.pe");

        as(token).body(Map.of("currentPassword", "password-del-staff-123", "newPassword", STRONG)).post("/auth/me/password").then().statusCode(204);

        as(token).get("/auth/me").then().statusCode(200);
    }
}

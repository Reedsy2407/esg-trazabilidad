package pe.esgtrazabilidad.auth.it;

import java.util.Map;
import java.util.UUID;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.restassured.response.Response;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.retention.RetentionConfig;
import pe.esgtrazabilidad.auth.retention.RetentionService;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/** CA-14 (claims side) and CA-15: the consent, its location part, the periods shown and the version. */
class RecyclerConsentIT extends RecyclerApiIT {

    @Autowired
    private RetentionService retentionService;

    @Autowired
    private StaffUserRepository staffUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** The periods are one global row shared by every test of the JVM: leave it as the migration made it. */
    @AfterEach
    void restoreDefaults() {
        jdbc.update("update retention_config set gps_days = 90, photo_months = 12, consent_version = 1 where id = 1");
    }

    private UUID newAdmin(StaffRole role) {
        StaffUser user = staffUsers.save(StaffUser.create(
                "retencion-" + UUID.randomUUID() + "@esgtrazabilidad.pe",
                passwordEncoder.encode("clave-" + UUID.randomUUID()),
                "Persona de prueba",
                role,
                false));
        return user.getId();
    }

    private Response answer(Session session, Object version, boolean accepted, boolean location) {
        return as(session.accessToken())
                .body(Map.of("version", version, "accepted", accepted, "locationAccepted", location))
                .post("/auth/recycler/consent");
    }

    /** The token to refresh with: each refresh rotates, so tests keep following the current one. */
    private Session refreshed(Session session) {
        Response response = refresh(session.refreshToken());
        response.then().statusCode(200);
        return new Session(
                session.accountId(),
                session.recyclerId(),
                session.associationId(),
                session.deviceId(),
                response.path("accessToken"),
                response.path("refreshToken"));
    }

    @Test
    void theConsentShowsTheConfiguredPeriodsInItsTextAndTheLocationPartIsSeparate() {
        Session session = activateSession();

        as(session.accessToken())
                .get("/auth/recycler/consent")
                .then()
                .statusCode(200)
                .body("version", equalTo(1))
                .body("retention.gpsDays", equalTo(90))
                .body("retention.photoMonths", equalTo(12))
                .body("text", containsString("12 meses"))
                .body("text", containsString("Ley N.° 29733"))
                .body("locationText", containsString("90 días"))
                .body("locationText", containsString("ubicación"));
    }

    @Test
    void theConsentRoutesNeedARecyclerToken() {
        as(staffToken(StaffRole.ADMIN)).get("/auth/recycler/consent").then().statusCode(403).body("code", equalTo("AUTH-000"));
        given().get("/auth/recycler/consent").then().statusCode(401);
        given().contentType("application/json")
                .body(Map.of("version", 1, "accepted", true, "locationAccepted", true))
                .post("/auth/recycler/consent")
                .then()
                .statusCode(401);
    }

    @Test
    void ca14TheTokenCarriesCvAndLcOnlyForWhatWasAccepted() throws Exception {
        Session session = activateSession();

        // Nothing accepted yet: no cv, no lc.
        JWTClaimsSet none = SignedJWT.parse(session.accessToken()).getJWTClaimsSet();
        assertThat(none.getClaim("cv")).isNull();
        assertThat(none.getClaim("lc")).isNull();

        // General consent without location.
        answer(session, 1, true, false).then().statusCode(204);
        session = refreshed(session);
        JWTClaimsSet general = SignedJWT.parse(session.accessToken()).getJWTClaimsSet();
        assertThat(general.getLongClaim("cv")).isEqualTo(1L);
        assertThat(general.getClaim("lc")).isNull();

        // Both.
        answer(session, 1, true, true).then().statusCode(204);
        session = refreshed(session);
        JWTClaimsSet both = SignedJWT.parse(session.accessToken()).getJWTClaimsSet();
        assertThat(both.getLongClaim("cv")).isEqualTo(1L);
        assertThat(both.getBooleanClaim("lc")).isTrue();

        // Withdrawing the general consent withdraws the location too, whatever was sent for it.
        answer(session, 1, false, true).then().statusCode(204);
        JWTClaimsSet withdrawn = SignedJWT.parse(refreshed(session).accessToken()).getJWTClaimsSet();
        assertThat(withdrawn.getClaim("cv")).isNull();
        assertThat(withdrawn.getClaim("lc")).isNull();
        assertThat(jdbc.queryForMap("select consent_version, location_consent from recycler_account where id = ?", session.accountId()))
                .containsEntry("consent_version", null)
                .containsEntry("location_consent", false);
    }

    @Test
    void ca15ChangingAPeriodRaisesTheVersionShowsTheNewTextAndAsksEveryoneAgain() throws Exception {
        Session session = activateSession();
        answer(session, 1, true, true).then().statusCode(204);
        session = refreshed(session);
        assertThat(SignedJWT.parse(session.accessToken()).getJWTClaimsSet().getLongClaim("cv")).isEqualTo(1L);

        RetentionConfig changed = retentionService.update(newAdmin(StaffRole.ADMIN), 60, 6);

        assertThat(changed).isEqualTo(new RetentionConfig(60, 6, 2));
        as(session.accessToken())
                .get("/auth/recycler/consent")
                .then()
                .body("version", equalTo(2))
                .body("retention.gpsDays", equalTo(60))
                .body("retention.photoMonths", equalTo(6))
                .body("text", containsString("6 meses"))
                .body("locationText", containsString("60 días"));

        // The next refresh omits cv (and lc with it) until the new version is accepted.
        session = refreshed(session);
        JWTClaimsSet asked = SignedJWT.parse(session.accessToken()).getJWTClaimsSet();
        assertThat(asked.getClaim("cv")).isNull();
        assertThat(asked.getClaim("lc")).isNull();

        // An answer to the old text is refused; to the new one, accepted.
        answer(session, 1, true, true).then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        answer(session, 2, true, true).then().statusCode(204);
        JWTClaimsSet again = SignedJWT.parse(refreshed(session).accessToken()).getJWTClaimsSet();
        assertThat(again.getLongClaim("cv")).isEqualTo(2L);
        assertThat(again.getBooleanClaim("lc")).isTrue();
    }

    @Test
    void savingTheSamePeriodsDoesNotAskAnybodyAgain() {
        UUID admin = newAdmin(StaffRole.ADMIN);
        retentionService.update(admin, 45, 9);

        RetentionConfig same = retentionService.update(admin, 45, 9);

        assertThat(same.consentVersion()).isEqualTo(2);
        assertThat(retentionService.update(admin, 45, 10).consentVersion()).isEqualTo(3);
    }

    @Test
    void onlyAnActiveAdminMayChangeThePeriodsAndNothingChangesOtherwise() {
        UUID staff = newAdmin(StaffRole.STAFF);

        assertThatThrownBy(() -> retentionService.update(staff, 30, 3))
                .isInstanceOf(ApplicationException.class)
                .extracting(e -> ((ApplicationException) e).getError())
                .isEqualTo(AuthErrors.ADMIN_REQUIRED);
        assertThatThrownBy(() -> retentionService.update(UUID.randomUUID(), 30, 3)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> retentionService.update(null, 30, 3)).isInstanceOf(ApplicationException.class);

        assertThat(jdbc.queryForMap("select gps_days, photo_months, consent_version from retention_config"))
                .containsEntry("gps_days", 90)
                .containsEntry("photo_months", 12)
                .containsEntry("consent_version", 1);
    }

    @Test
    void periodsOutOfRangeAreRefused() {
        UUID admin = newAdmin(StaffRole.ADMIN);

        assertThatThrownBy(() -> retentionService.update(admin, RetentionService.MIN_GPS_DAYS - 1, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retentionService.update(admin, 90, RetentionService.MIN_PHOTO_MONTHS - 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retentionService.update(admin, RetentionService.MAX_GPS_DAYS + 1, 12))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> retentionService.update(admin, 90, RetentionService.MAX_PHOTO_MONTHS + 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select consent_version from retention_config", Integer.class)).isEqualTo(1);
    }

    @Test
    void anIncompleteAnswerIsAValidationErrorAndRecordsNothing() {
        Session session = activateSession();

        as(session.accessToken()).body("{}").post("/auth/recycler/consent").then().statusCode(400).body("code", equalTo("VALIDATION_ERROR"));
        as(session.accessToken())
                .body(Map.of("version", 1, "accepted", true))
                .post("/auth/recycler/consent")
                .then()
                .statusCode(400);

        assertThat(jdbc.queryForObject("select consent_version from recycler_account where id = ?", Integer.class, session.accountId()))
                .isNull();
    }
}

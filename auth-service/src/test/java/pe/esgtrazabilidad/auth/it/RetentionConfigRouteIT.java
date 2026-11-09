package pe.esgtrazabilidad.auth.it;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.retention.RetentionService;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** CA-15 (route): GET and PUT /auth/retention-config, ADMIN only, with the decided ranges. */
class RetentionConfigRouteIT extends RecyclerApiIT {

    /** The periods are one global row shared by every test of the JVM: leave it as the migration made it. */
    @AfterEach
    void restoreDefaults() {
        jdbc.update("update retention_config set gps_days = 90, photo_months = 12, consent_version = 1 where id = 1");
    }

    private Integer storedConsentVersion() {
        return jdbc.queryForObject("select consent_version from retention_config where id = 1", Integer.class);
    }

    @Test
    void anAdminReadsTheDefaultsAndChangesThemAndTheConsentVersionRises() {
        String admin = staffToken(StaffRole.ADMIN);

        as(admin).get("/auth/retention-config")
                .then()
                .statusCode(200)
                .body("gpsDays", equalTo(90))
                .body("photoMonths", equalTo(12))
                .body("consentVersion", equalTo(1));

        as(admin).body(Map.of("gpsDays", 60, "photoMonths", 6))
                .put("/auth/retention-config")
                .then()
                .statusCode(200)
                .body("gpsDays", equalTo(60))
                .body("photoMonths", equalTo(6))
                .body("consentVersion", equalTo(2));

        // The same values again ask nobody a second time.
        as(admin).body(Map.of("gpsDays", 60, "photoMonths", 6))
                .put("/auth/retention-config")
                .then()
                .statusCode(200)
                .body("consentVersion", equalTo(2));
        as(admin).get("/auth/retention-config").then().statusCode(200).body("gpsDays", equalTo(60));
    }

    @Test
    void theRangesAreInclusiveAtBothEnds() {
        String admin = staffToken(StaffRole.ADMIN);

        as(admin).body(Map.of("gpsDays", RetentionService.MIN_GPS_DAYS, "photoMonths", RetentionService.MIN_PHOTO_MONTHS))
                .put("/auth/retention-config")
                .then()
                .statusCode(200);
        as(admin).body(Map.of("gpsDays", RetentionService.MAX_GPS_DAYS, "photoMonths", RetentionService.MAX_PHOTO_MONTHS))
                .put("/auth/retention-config")
                .then()
                .statusCode(200);
    }

    @Test
    void outOfRangeOrIncompleteIsAValidationErrorAndChangesNothing() {
        String admin = staffToken(StaffRole.ADMIN);

        for (Map<String, Object> body : java.util.List.<Map<String, Object>>of(
                Map.of("gpsDays", RetentionService.MIN_GPS_DAYS - 1, "photoMonths", 12),
                Map.of("gpsDays", RetentionService.MAX_GPS_DAYS + 1, "photoMonths", 12),
                Map.of("gpsDays", 90, "photoMonths", RetentionService.MIN_PHOTO_MONTHS - 1),
                Map.of("gpsDays", 90, "photoMonths", RetentionService.MAX_PHOTO_MONTHS + 1),
                Map.of("gpsDays", 90),
                Map.<String, Object>of())) {
            as(admin).body(body)
                    .put("/auth/retention-config")
                    .then()
                    .statusCode(400)
                    .body("code", equalTo("VALIDATION_ERROR"));
        }

        assertThat(storedConsentVersion()).isEqualTo(1);
        assertThat(jdbc.queryForMap("select gps_days, photo_months from retention_config"))
                .containsEntry("gps_days", 90)
                .containsEntry("photo_months", 12);
    }

    @Test
    void staffGetsAuth000OnBothVerbsAndNothingChanges() {
        String staff = staffToken(StaffRole.STAFF);

        as(staff).get("/auth/retention-config")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
        as(staff).body(Map.of("gpsDays", 60, "photoMonths", 6))
                .put("/auth/retention-config")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));

        assertThat(storedConsentVersion()).isEqualTo(1);
    }

    @Test
    void aRecyclerTokenAndNoTokenAreRefused() {
        Session session = activateSession();

        as(session.accessToken()).get("/auth/retention-config").then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(session.accessToken())
                .body(Map.of("gpsDays", 60, "photoMonths", 6))
                .put("/auth/retention-config")
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
        given().get("/auth/retention-config").then().statusCode(401).body("code", equalTo("AUTH-000"));

        assertThat(storedConsentVersion()).isEqualTo(1);
    }
}

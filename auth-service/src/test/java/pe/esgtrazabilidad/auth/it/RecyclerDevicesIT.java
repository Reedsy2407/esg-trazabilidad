package pe.esgtrazabilidad.auth.it;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.restassured.response.Response;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;

/** CA-12 (IT part): staff list an account's phones and revoke one; its refresh stops at once. */
class RecyclerDevicesIT extends RecyclerApiIT {

    private static final String DEVICES = "/auth/recycler-accounts/{id}/devices";

    @Test
    void staffSeeEachPhoneWithoutAnyCredentialAndTheListGrowsAfterAReissue() {
        Session first = activateSession();
        clock.advance(Duration.ofMinutes(3));
        String staff = staffToken(StaffRole.STAFF);

        Response list = as(staff).get(DEVICES, first.accountId());

        list.then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].deviceId", equalTo(first.deviceId().toString()))
                .body("[0].label", equalTo("Honor X7b de prueba"))
                .body("[0].revokedAt", nullValue());
        // Exactly these five fields: no hash, no token, no install id.
        assertThat(list.jsonPath().getMap("[0]").keySet())
                .containsExactlyInAnyOrder("deviceId", "label", "createdAt", "lastSeenAt", "revokedAt");
        assertThat(Instant.parse(list.path("[0].createdAt"))).isEqualTo(START);
        assertThat(list.asString()).doesNotContain(first.refreshToken());

        // The phone is lost: revoke it, ask for a new code, activate the new phone. Both are listed.
        as(staff).delete(DEVICES + "/{device}", first.accountId(), first.deviceId()).then().statusCode(204);
        Response reissued = as(staff).post("/auth/recycler-accounts/{id}/activation-code", first.accountId());
        reissued.then().statusCode(201);
        activate(reissued.path("activationCode"), "install-new-phone", uniqueAddress()).then().statusCode(200);

        List<Map<String, Object>> devices = as(staff).get(DEVICES, first.accountId()).jsonPath().getList("$");
        assertThat(devices).hasSize(2);
        assertThat(devices.get(0).get("revokedAt")).isNotNull();
        assertThat(devices.get(1).get("revokedAt")).isNull();
        assertThat(as(staff).get("/auth/recycler-accounts").jsonPath().getList("findAll { it.accountId == '" + first.accountId() + "' }.devices"))
                .containsExactly(1);
    }

    @Test
    void ca12ARevokedDeviceCannotRefreshAndOtherDevicesAreUntouched() {
        Session lost = activateSession();
        Session other = activateSession();
        String admin = staffToken(StaffRole.ADMIN);

        as(admin).delete(DEVICES + "/{device}", lost.accountId(), lost.deviceId()).then().statusCode(204);

        refresh(lost.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-017"));
        refresh(other.refreshToken()).then().statusCode(200);
    }

    @Test
    void revokingTwiceIsFineAndKeepsTheFirstRevocationTime() {
        Session session = activateSession();
        String staff = staffToken(StaffRole.STAFF);

        as(staff).delete(DEVICES + "/{device}", session.accountId(), session.deviceId()).then().statusCode(204);
        Instant first = jdbc.queryForObject(
                "select revoked_at from recycler_device where id = ?", java.sql.Timestamp.class, session.deviceId()).toInstant();
        clock.advance(Duration.ofHours(1));
        as(staff).delete(DEVICES + "/{device}", session.accountId(), session.deviceId()).then().statusCode(204);

        assertThat(jdbc.queryForObject(
                        "select revoked_at from recycler_device where id = ?", java.sql.Timestamp.class, session.deviceId()).toInstant())
                .isEqualTo(first);
    }

    @Test
    void aDeviceOfAnotherAccountOrAnUnknownOneIs404AndNothingIsRevoked() {
        Session mine = activateSession();
        Session theirs = activateSession();
        String staff = staffToken(StaffRole.STAFF);

        as(staff).delete(DEVICES + "/{device}", mine.accountId(), theirs.deviceId()).then().statusCode(404).body("code", equalTo("NOT_FOUND"));
        as(staff).delete(DEVICES + "/{device}", mine.accountId(), UUID.randomUUID()).then().statusCode(404);
        as(staff).delete(DEVICES + "/{device}", UUID.randomUUID(), mine.deviceId()).then().statusCode(404);
        as(staff).get(DEVICES, UUID.randomUUID()).then().statusCode(404);

        refresh(theirs.refreshToken()).then().statusCode(200);
        refresh(mine.refreshToken()).then().statusCode(200);
    }

    @Test
    void onlyStaffMayListOrRevoke() {
        Session session = activateSession();

        as(session.accessToken()).get(DEVICES, session.accountId()).then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(session.accessToken())
                .delete(DEVICES + "/{device}", session.accountId(), session.deviceId())
                .then()
                .statusCode(403)
                .body("code", equalTo("AUTH-000"));
        given().get(DEVICES, session.accountId()).then().statusCode(401);
        given().delete(DEVICES + "/{device}", session.accountId(), session.deviceId()).then().statusCode(401);

        refresh(session.refreshToken()).then().statusCode(200);
    }
}

package pe.esgtrazabilidad.auth.it;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import io.restassured.response.Response;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;
import pe.esgtrazabilidad.auth.recycleraccount.service.RefreshTokens;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/** CA-8 to CA-12 (refresh side): rotation with a grace window, expiries, live status, revocation, logout. */
@ExtendWith(OutputCaptureExtension.class)
class RecyclerRefreshIT extends RecyclerApiIT {

    private String currentHash(UUID deviceId) {
        return jdbc.queryForObject("select refresh_hash from recycler_device where id = ?", String.class, deviceId);
    }

    private boolean revoked(UUID deviceId) {
        return jdbc.queryForObject("select revoked_at is not null from recycler_device where id = ?", Boolean.class, deviceId);
    }

    @Test
    void ca8ARefreshGivesNewAccessAndRefreshTokensAndTheOldOneKeepsWorkingForTenMinutes() throws Exception {
        Session session = activateSession();
        clock.advance(Duration.ofMinutes(5));

        Response rotated = refresh(session.refreshToken());

        rotated.then().statusCode(200).header("Cache-Control", equalTo("no-store"));
        String t1 = rotated.path("refreshToken");
        assertThat(t1).isNotEqualTo(session.refreshToken()).hasSize(43);
        assertThat(rotated.<Integer>path("expiresIn")).isEqualTo(900);
        JWTClaimsSet claims = SignedJWT.parse(rotated.<String>path("accessToken")).getJWTClaimsSet();
        assertThat(claims.getSubject()).isEqualTo(session.accountId().toString());
        assertThat(claims.getStringClaim("did")).isEqualTo(session.deviceId().toString());
        assertThat(claims.getStringClaim("aid")).isEqualTo(session.associationId().toString());
        assertThat(claims.getStringClaim("rid")).isEqualTo(session.recyclerId().toString());
        assertThat(claims.getExpirationTime().toInstant()).isEqualTo(START.plus(Duration.ofMinutes(5 + 15)));

        // Stored only as hashes: the new token's hash is current, the old one's is the previous.
        Map<String, Object> device = jdbc.queryForMap("select * from recycler_device where id = ?", session.deviceId());
        assertThat(device.get("refresh_hash")).isEqualTo(RefreshTokens.hash(t1));
        assertThat(device.get("previous_hash")).isEqualTo(RefreshTokens.hash(session.refreshToken()));
        assertThat(((java.sql.Timestamp) device.get("previous_valid_until")).toInstant())
                .isEqualTo(START.plus(Duration.ofMinutes(15)));
        assertThat(device.values().stream().map(String::valueOf)).doesNotContain(t1, session.refreshToken());

        // The old token, inside the window (also exactly at its end): same rotation, never a revocation.
        clock.advance(Duration.ofMinutes(9));
        Response again = refresh(session.refreshToken());
        again.then().statusCode(200);
        assertThat(again.<String>path("refreshToken")).isEqualTo(t1);
        clock.advance(Duration.ofMinutes(1)); // exactly previous_valid_until
        assertThat(refresh(session.refreshToken()).<String>path("refreshToken")).isEqualTo(t1);
        assertThat(revoked(session.deviceId())).isFalse();

        // And the new one keeps rotating.
        Response next = refresh(t1);
        next.then().statusCode(200);
        assertThat(next.<String>path("refreshToken")).isNotIn(t1, session.refreshToken());
    }

    @Test
    void aGraceRepeatWhoseDerivedTokenDoesNotMatchTheStoredOneIsRefusedNotHandedAToken() {
        Session session = activateSession();
        refresh(session.refreshToken()).then().statusCode(200);
        // As if the derivation key had changed: the stored current hash is no longer what derive() yields.
        jdbc.update("update recycler_device set refresh_hash = ? where id = ?", "0".repeat(64), session.deviceId());

        refresh(session.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-016"));
        assertThat(revoked(session.deviceId())).isFalse();
    }

    @Test
    void ca9TheOldTokenAfterTheWindowRevokesTheDeviceAndTheCurrentTokenStopsWorkingToo() {
        Session session = activateSession();
        String t1 = refresh(session.refreshToken()).path("refreshToken");
        clock.advance(Duration.ofMinutes(10).plusSeconds(1));

        refresh(session.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-017"));

        assertThat(revoked(session.deviceId())).isTrue();
        refresh(t1).then().statusCode(401).body("code", equalTo("AUTH-017"));
        refresh(session.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-017"));
    }

    @Test
    void ca10ADeviceUnusedForThirtyDaysCannotRefreshButOneJustInsideTheLimitCan() {
        Session unused = activateSession();
        clock.advance(Duration.ofDays(30).minusSeconds(1));
        refresh(unused.refreshToken()).then().statusCode(200);

        Session tooLate = activateSession();
        clock.advance(Duration.ofDays(30));
        refresh(tooLate.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-016"));
        assertThat(revoked(tooLate.deviceId())).as("expiry is not a revocation").isFalse();
    }

    @Test
    void ca10EvenAnActiveDeviceStopsAtNinetyDaysFromItsActivation() {
        Session session = activateSession();
        String token = session.refreshToken();
        for (int day = 20; day <= 80; day += 20) {
            clock.set(START.plus(Duration.ofDays(day)));
            Response response = refresh(token);
            response.then().statusCode(200);
            token = response.path("refreshToken");
        }
        clock.set(START.plus(Duration.ofDays(90)).minusSeconds(1));
        Response lastDay = refresh(token);
        lastDay.then().statusCode(200);
        token = lastDay.path("refreshToken");

        clock.set(START.plus(Duration.ofDays(90)));
        refresh(token).then().statusCode(401).body("code", equalTo("AUTH-016"));
    }

    @Test
    void anUnknownOrMalformedTokenIsAuth016() {
        refresh("x".repeat(43)).then().statusCode(401).body("code", equalTo("AUTH-016"));
        refresh("basura").then().statusCode(401).body("code", equalTo("AUTH-016"));
        given().contentType("application/json").body("{}").post("/auth/recycler/refresh").then().statusCode(400);
    }

    @Test
    void ca12ADeviceRevokedByStaffCannotRefreshAndOneRevokedDoesNotAffectTheOthers() {
        Session revoked = activateSession();
        Session other = activateSession();
        jdbc.update("update recycler_device set revoked_at = now() where id = ?", revoked.deviceId());

        refresh(revoked.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-017"));
        refresh(other.refreshToken()).then().statusCode(200);
    }

    @Test
    void ca11ARecyclerTurnedOffGetsAuth018OnRefreshWithoutRotatingAndComesBackWhenReactivated() {
        Session session = activateSession();
        Instant off = Instant.now().plusSeconds(60);
        setCopyStatus(session.recyclerId(), session.associationId(), RecyclerStatus.INACTIVE, off);
        String hashBefore = currentHash(session.deviceId());

        refresh(session.refreshToken()).then().statusCode(403).body("code", equalTo("AUTH-018"));
        // The same event delivered again changes nothing.
        setCopyStatus(session.recyclerId(), session.associationId(), RecyclerStatus.INACTIVE, off);
        refresh(session.refreshToken()).then().statusCode(403).body("code", equalTo("AUTH-018"));
        assertThat(currentHash(session.deviceId())).isEqualTo(hashBefore);
        assertThat(revoked(session.deviceId())).as("inactive is not a revocation").isFalse();

        setCopyStatus(session.recyclerId(), session.associationId(), RecyclerStatus.ACTIVE, off.plusSeconds(60));
        refresh(session.refreshToken()).then().statusCode(200);
    }

    @Test
    void aRecyclerWithNoStatusCopyFailsClosed() {
        Session session = activateSession();
        jdbc.update("delete from recycler_status_copy_auth where recycler_id = ?", session.recyclerId());

        refresh(session.refreshToken()).then().statusCode(403).body("code", equalTo("AUTH-018"));
    }

    @Test
    void manyRefreshesOfTheSameTokenAtOnceRotateOnceAndAllGetTheSameAnswer() throws Exception {
        Session session = activateSession();
        int callers = 6;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<Response> responses = new ArrayList<>();
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Response>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return refresh(session.refreshToken());
                }));
            }
            start.countDown();
            for (Future<Response> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(responses).allMatch(r -> r.statusCode() == 200);
        assertThat(responses.stream().map(r -> r.<String>path("refreshToken")).distinct()).hasSize(1);
        assertThat(jdbc.queryForObject("select refresh_generation from recycler_device where id = ?", Integer.class, session.deviceId()))
                .isEqualTo(1);
        assertThat(revoked(session.deviceId())).isFalse();
    }

    @Test
    void logoutRevokesThatDeviceAndIsIdempotent() {
        Session session = activateSession();

        as(session.accessToken()).body(Map.of("refreshToken", session.refreshToken())).post("/auth/recycler/logout").then().statusCode(204);

        assertThat(revoked(session.deviceId())).isTrue();
        refresh(session.refreshToken()).then().statusCode(401).body("code", equalTo("AUTH-017"));
        as(session.accessToken()).body(Map.of("refreshToken", session.refreshToken())).post("/auth/recycler/logout").then().statusCode(204);
    }

    @Test
    void logoutWithAnotherAccountsTokenOrAnUnknownOneChangesNothing() {
        Session mine = activateSession();
        Session theirs = activateSession();

        as(mine.accessToken()).body(Map.of("refreshToken", theirs.refreshToken())).post("/auth/recycler/logout").then().statusCode(401).body("code", equalTo("AUTH-016"));
        as(mine.accessToken()).body(Map.of("refreshToken", "x".repeat(43))).post("/auth/recycler/logout").then().statusCode(401);

        assertThat(revoked(theirs.deviceId())).isFalse();
        assertThat(revoked(mine.deviceId())).isFalse();
    }

    @Test
    void logoutNeedsARecyclerToken() {
        Session session = activateSession();
        Map<String, String> body = Map.of("refreshToken", session.refreshToken());

        given().contentType("application/json").body(body).post("/auth/recycler/logout").then().statusCode(401);
        as(staffToken(StaffRole.ADMIN)).body(body).post("/auth/recycler/logout").then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(session.accessToken()).body("{}").post("/auth/recycler/logout").then().statusCode(400);
        assertThat(revoked(session.deviceId())).isFalse();
    }

    @Test
    void noRefreshTokenReachesTheLogs(CapturedOutput output) {
        Session session = activateSession();
        Response rotated = refresh(session.refreshToken());
        clock.advance(Duration.ofMinutes(11));
        refresh(session.refreshToken()); // reuse: logged as a revocation, without the token

        assertThat(output.getAll())
                .doesNotContain(session.refreshToken())
                .doesNotContain(rotated.<String>path("refreshToken"))
                .doesNotContain(rotated.<String>path("accessToken"))
                .doesNotContain(RefreshTokens.hash(session.refreshToken()));
    }
}

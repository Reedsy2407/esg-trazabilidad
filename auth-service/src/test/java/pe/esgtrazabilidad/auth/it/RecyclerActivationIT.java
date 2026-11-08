package pe.esgtrazabilidad.auth.it;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** CA-6: activation trades a one-use code for a RECYCLER session; every bad code answers alike. */
@ExtendWith(OutputCaptureExtension.class)
class RecyclerActivationIT extends RecyclerApiIT {

    private static JWTClaimsSet claimsOf(String token) throws Exception {
        return SignedJWT.parse(token).getJWTClaimsSet();
    }

    @Test
    void aValidCodeGivesATokenForThatRecyclerAndTheFirstRefreshAndSpendsTheCode() throws Exception {
        IssuedAccount issued = issueAccount();

        Response response = activate(issued.code(), "install-a", uniqueAddress());

        response.then().statusCode(200).header("Cache-Control", equalTo("no-store"));
        String access = response.path("accessToken");
        String refresh = response.path("refreshToken");
        assertThat(refresh).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(response.<Integer>path("expiresIn")).isEqualTo(900);
        assertThat(response.<Boolean>path("consentRequired")).isTrue();
        assertThat(response.<Integer>path("consentVersion")).isEqualTo(1);

        Map<String, Object> device =
                jdbc.queryForMap("select * from recycler_device where account_id = ?", issued.accountId());
        JWTClaimsSet claims = claimsOf(access);
        assertThat(claims.getSubject()).isEqualTo(issued.accountId().toString());
        assertThat(claims.getStringClaim("role")).isEqualTo("RECYCLER");
        assertThat(claims.getStringClaim("rid")).isEqualTo(issued.recyclerId().toString());
        assertThat(claims.getStringClaim("aid")).isEqualTo(issued.associationId().toString());
        assertThat(claims.getStringClaim("did")).isEqualTo(device.get("id").toString());
        assertThat(claims.getClaim("cv")).as("no consent yet").isNull();
        assertThat(claims.getClaim("lc")).isNull();
        assertThat(claims.getExpirationTime().toInstant())
                .isEqualTo(claims.getIssueTime().toInstant().plus(Duration.ofMinutes(15)));

        // The refresh token is stored only as its hash; the table never holds the token.
        assertThat(device.get("refresh_hash")).isEqualTo(RefreshTokens.hash(refresh)).isNotEqualTo(refresh);
        assertThat(device.values().stream().map(String::valueOf)).doesNotContain(refresh);
        assertThat(device.get("label")).isEqualTo("Honor X7b de prueba");
        assertThat(device.get("install_id")).isEqualTo("install-a");
        assertThat(device.get("revoked_at")).isNull();
        assertThat(((java.sql.Timestamp) device.get("absolute_expires_at")).toInstant())
                .isEqualTo(START.plus(Duration.ofDays(90)));

        Map<String, Object> account = jdbc.queryForMap("select * from recycler_account where id = ?", issued.accountId());
        assertThat(account.get("status")).isEqualTo("ACTIVE");
        assertThat(account.get("activated_at")).isNotNull();
        assertThat(jdbc.queryForObject(
                        "select used_at is not null from activation_code where account_id = ?", Boolean.class, issued.accountId()))
                .isTrue();
    }

    @Test
    void theIssuedTokenIsAcceptedBySignatureAndRejectedByRoleOnStaffRoutes() {
        IssuedAccount issued = issueAccount();
        String access = activate(issued.code(), "install-b", uniqueAddress()).path("accessToken");

        // 403 AUTH-000 (role), not 401: the signature and expiry were accepted.
        as(access).get("/auth/recycler-accounts").then().statusCode(403).body("code", equalTo("AUTH-000"));
        as(access).get("/auth/me").then().statusCode(403).body("code", equalTo("AUTH-000"));
    }

    @Test
    void usingTheSameCodeAgainIsAuth014() {
        IssuedAccount issued = issueAccount();
        activate(issued.code(), "install-c", uniqueAddress()).then().statusCode(200);

        activate(issued.code(), "install-d", uniqueAddress())
                .then()
                .statusCode(400)
                .body("code", equalTo("AUTH-014"));
        assertThat(jdbc.queryForObject("select count(*) from recycler_device where account_id = ?", Integer.class, issued.accountId()))
                .isEqualTo(1);
    }

    @Test
    void anExpiredAnUnknownAReplacedAndAMalformedCodeAllAnswerExactlyTheSame() {
        IssuedAccount expired = issueAccount();
        clock.advance(Duration.ofHours(72).plusSeconds(1));
        Response expiredResponse = activate(expired.code(), "install-e", uniqueAddress());

        clock.set(START);
        Response unknown = activate(unissuedCode(), "install-f", uniqueAddress());

        IssuedAccount replaced = issueAccount();
        String staff = staffToken(StaffRole.STAFF);
        as(staff).post("/auth/recycler-accounts/{id}/activation-code", replaced.accountId()).then().statusCode(201);
        Response replacedResponse = activate(replaced.code(), "install-g", uniqueAddress());

        Response malformed = activate("corto", "install-h", uniqueAddress());
        Response ambiguousSymbols = activate("O0I1O0I1O0", "install-i", uniqueAddress());

        for (Response bad : List.of(expiredResponse, unknown, replacedResponse, malformed, ambiguousSymbols)) {
            bad.then().statusCode(400).body("code", equalTo("AUTH-014"));
        }
        assertThat(List.of(unknown, replacedResponse, malformed, ambiguousSymbols).stream()
                        .map(r -> r.<String>path("detail"))
                        .distinct())
                .hasSize(1)
                .containsExactly(expiredResponse.<String>path("detail"));
    }

    @Test
    void theCodeIsAcceptedInLowerCaseWithSeparators() {
        IssuedAccount issued = issueAccount();
        String typed = issued.code().substring(0, 5).toLowerCase() + "-" + issued.code().substring(5).toLowerCase();

        activate(typed, "install-j", uniqueAddress()).then().statusCode(200);
    }

    @Test
    void aRecyclerTurnedOffGetsAuth018AndTheCodeIsNotSpent() {
        IssuedAccount issued = issueAccount();
        setCopyStatus(issued.recyclerId(), issued.associationId(), RecyclerStatus.INACTIVE, Instant.now().plusSeconds(60));

        activate(issued.code(), "install-k", uniqueAddress()).then().statusCode(403).body("code", equalTo("AUTH-018"));
        assertThat(jdbc.queryForObject("select count(*) from recycler_device where account_id = ?", Integer.class, issued.accountId()))
                .isZero();

        // Reactivated within the 72 hours: the same code still works.
        setCopyStatus(issued.recyclerId(), issued.associationId(), RecyclerStatus.ACTIVE, Instant.now().plusSeconds(120));
        activate(issued.code(), "install-k", uniqueAddress()).then().statusCode(200);
    }

    @Test
    void anAlreadyAcceptedConsentOfTheCurrentVersionTravelsInTheNewToken() throws Exception {
        IssuedAccount issued = issueAccount();
        jdbc.update(
                "update recycler_account set consent_version = 1, consent_accepted_at = now(), location_consent = true where id = ?",
                issued.accountId());

        Response response = activate(issued.code(), "install-l", uniqueAddress());

        assertThat(response.<Boolean>path("consentRequired")).isFalse();
        JWTClaimsSet claims = claimsOf(response.path("accessToken"));
        assertThat(claims.getLongClaim("cv")).isEqualTo(1L);
        assertThat(claims.getBooleanClaim("lc")).isTrue();
    }

    @Test
    void missingFieldsAreValidationErrorsAndDoNotTouchTheCode() {
        IssuedAccount issued = issueAccount();

        given().contentType("application/json")
                .body(Map.of("code", issued.code()))
                .post("/auth/recycler/activate")
                .then()
                .statusCode(400)
                .body("code", equalTo("VALIDATION_ERROR"));
        given().contentType("application/json")
                .body(Map.of("code", issued.code(), "deviceLabel", " ", "deviceInstallId", "x"))
                .post("/auth/recycler/activate")
                .then()
                .statusCode(400);

        activate(issued.code(), "install-m", uniqueAddress()).then().statusCode(200);
    }

    @Test
    void theSameCodeUsedByManyAtOnceSucceedsExactlyOnce() throws Exception {
        IssuedAccount issued = issueAccount();
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        List<Integer> statuses = new ArrayList<>();
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                String install = "install-race-" + i;
                String address = uniqueAddress();
                futures.add(pool.submit(() -> {
                    start.await();
                    return activate(issued.code(), install, address).statusCode();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        // One winner; every loser is refused (400 invalid code, or 429 once that code's own failure limit
        // is reached: both are correct answers to a code that is already spent).
        assertThat(statuses).filteredOn(s -> s == 200).hasSize(1);
        assertThat(statuses).filteredOn(s -> s != 200).hasSize(callers - 1).allMatch(s -> s == 400 || s == 429);
        assertThat(jdbc.queryForObject("select count(*) from recycler_device where account_id = ?", Integer.class, issued.accountId()))
                .isEqualTo(1);
    }

    @Test
    void neitherTheCodeNorAnyTokenReachesTheLogs(CapturedOutput output) {
        IssuedAccount issued = issueAccount();
        String address = uniqueAddress();
        Response ok = activate(issued.code(), "install-n", address);
        String typedWrong = unissuedCode();
        activate(typedWrong, "install-o", address);

        assertThat(output.getAll())
                .doesNotContain(issued.code())
                .doesNotContain(typedWrong)
                .doesNotContain(ok.<String>path("refreshToken"))
                .doesNotContain(ok.<String>path("accessToken"))
                .doesNotContain(address);
    }
}

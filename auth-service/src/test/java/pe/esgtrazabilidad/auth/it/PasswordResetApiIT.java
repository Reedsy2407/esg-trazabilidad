package pe.esgtrazabilidad.auth.it;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.email.EmailSender;
import pe.esgtrazabilidad.auth.email.FakeEmailSender;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Password recovery end to end against a real Postgres, with the fake email sender: the whole flow,
 * the limits kept in the database, the attempts, the expiry, the race between two confirmations, the
 * uniform answer, and a provider that is down.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=password-reset-api-it-test-secret-32-bytes!!")
class PasswordResetApiIT {

    private static final String STRONG = "una-clave-larga-y-segura";
    private static final String NEW_PASSWORD = "otra-clave-larga-y-distinta";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

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
    private StaffUserRepository staffUsers;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EmailSender emailSender;

    @Autowired
    private JdbcTemplate jdbc;

    private FakeEmailSender fake;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        fake = (FakeEmailSender) emailSender;
        fake.clear();
    }

    /** Each caller gets its own address (the rightmost X-Forwarded-For entry), so the per-address limits do not mix. */
    private static String freshIp() {
        int n = IP_SEQUENCE.incrementAndGet();
        return "198.51." + (n / 250) + "." + (n % 250 + 1);
    }

    private static String freshEmail() {
        return "persona" + SEQUENCE.incrementAndGet() + "@esgtrazabilidad.pe";
    }

    private StaffUser account(String email) {
        return staffUsers.save(StaffUser.create(email, passwordEncoder.encode(STRONG), "Persona " + email, StaffRole.STAFF, false));
    }

    private Response request(String email, String ip) {
        return given().contentType("application/json")
                .header("X-Forwarded-For", ip)
                .body(Map.of("email", email))
                .post("/auth/password-reset/request");
    }

    private Response verify(String email, String code, String ip) {
        return given().contentType("application/json")
                .header("X-Forwarded-For", ip)
                .body(Map.of("email", email, "code", code))
                .post("/auth/password-reset/verify");
    }

    private Response confirm(String token, String password, String ip) {
        return given().contentType("application/json")
                .header("X-Forwarded-For", ip)
                .body(Map.of("resetToken", token, "newPassword", password))
                .post("/auth/password-reset/confirm");
    }

    /** Asks for a code, reads it from the fake mailbox and trades it for a reset token. */
    private String tokenFor(String email) {
        request(email, freshIp()).then().statusCode(202);
        return verify(email, codeSentTo(email), freshIp()).then().statusCode(200).extract().path("resetToken");
    }

    private String codeSentTo(String email) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> fake.lastCodeFor(email).isPresent());
        return fake.lastCodeFor(email).orElseThrow();
    }

    private int loginStatus(String email, String password) {
        return given().contentType("application/json")
                .body(Map.of("email", email, "password", password))
                .post("/auth/login")
                .statusCode();
    }

    /** Pretends the earlier requests happened a while ago, past the 60 second gap. */
    private void ageRequests() {
        jdbc.update("UPDATE password_reset_request SET requested_at = requested_at - interval '2 minutes'");
    }

    private long codesSent() {
        return fake.sent().stream().filter(s -> s.kind().equals("reset_code")).count();
    }

    @Test
    void theWholeFlowChangesThePasswordSpendsTheCodeAndTellsThePersonOnce() {
        String email = freshEmail();
        StaffUser user = account(email);
        String ip = freshIp();

        request(email, ip).then().statusCode(202);
        String code = codeSentTo(email);
        assertThat(code).matches("\\d{8}");
        String token = verify(email, code, ip).then().statusCode(200).extract().path("resetToken");
        // The token is a UUID, a dot and 43 URL-safe characters (32 random bytes), and the password is still the old one.
        assertThat(token).matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}");
        assertThat(loginStatus(email, STRONG)).isEqualTo(200);
        // The code was spent by the verification.
        verify(email, code, ip).then().statusCode(400).body("code", equalTo("AUTH-011"));
        confirm(token, NEW_PASSWORD, ip).then().statusCode(204);

        assertThat(loginStatus(email, STRONG)).isEqualTo(401);
        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(200);
        StaffUser after = staffUsers.findById(user.getId()).orElseThrow();
        assertThat(after.getPasswordChangedAt()).isNotNull();
        assertThat(after.isMustChangePassword()).isFalse();
        // A used token cannot be used again.
        confirm(token, "una-tercera-clave-larga-y-distinta", ip).then().statusCode(400).body("code", equalTo("AUTH-011"));
        // The notice goes out once, to that address, and says nothing secret.
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> fake.sent().stream().anyMatch(s -> s.kind().equals("password_changed") && s.to().equals(email)));
        assertThat(fake.sent().stream().filter(s -> s.kind().equals("password_changed") && s.to().equals(email)).count())
                .isEqualTo(1);
    }

    @Test
    void existingUnknownAndInactiveAccountsGetTheSameStatusAndBodyAndOnlyTheActiveOneGetsACode() {
        String active = freshEmail();
        account(active);
        String inactive = freshEmail();
        StaffUser off = account(inactive);
        staffUsers.save(off.deactivate("baja", off.getId(), java.time.Instant.now()));
        String unknown = freshEmail();

        Response a = request(active, freshIp());
        Response b = request(inactive, freshIp());
        Response c = request(unknown, freshIp());

        for (Response response : List.of(a, b, c)) {
            response.then().statusCode(202);
        }
        assertThat(b.asString()).isEqualTo(a.asString());
        assertThat(c.asString()).isEqualTo(a.asString());
        assertThat(b.headers().size()).isEqualTo(a.headers().size());
        await().atMost(5, TimeUnit.SECONDS).until(() -> fake.lastCodeFor(active).isPresent());
        assertThat(fake.lastCodeFor(inactive)).isEmpty();
        assertThat(fake.lastCodeFor(unknown)).isEmpty();
        // Neither the email nor the code is stored in the clear.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_request WHERE email_hmac = ?", Integer.class, active))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM password_reset_code WHERE code_hmac = ?", Integer.class, fake.lastCodeFor(active).get()))
                .isZero();
    }

    @Test
    void theRoutesArePublicAndRejectBodiesWithoutAnEmail() {
        given().contentType("application/json").body("{}").post("/auth/password-reset/request").then().statusCode(400);
        given().contentType("application/json").body("{}").post("/auth/password-reset/verify").then().statusCode(400);
        given().contentType("application/json").body("{}").post("/auth/password-reset/confirm").then().statusCode(400);
        request("no-es-un-correo", freshIp()).then().statusCode(202); // the same answer for any format
    }

    @Test
    void aSecondRequestWithinAMinuteAndAFourthWithinAnHourAnswerTheSameButSendNothing() {
        String email = freshEmail();
        account(email);

        request(email, freshIp()).then().statusCode(202);
        codeSentTo(email);
        request(email, freshIp()).then().statusCode(202); // inside the 60 second gap
        assertThat(codesSent()).isEqualTo(1);

        ageRequests();
        request(email, freshIp()).then().statusCode(202);
        await().atMost(5, TimeUnit.SECONDS).until(() -> codesSent() == 2);
        ageRequests();
        request(email, freshIp()).then().statusCode(202);
        await().atMost(5, TimeUnit.SECONDS).until(() -> codesSent() == 3);
        ageRequests();
        request(email, freshIp()).then().statusCode(202); // the fourth in the hour
        assertThat(codesSent()).isEqualTo(3);
    }

    @Test
    void eleventhRequestFromOneAddressIsRefusedWith429AndRetryAfter() {
        String ip = freshIp();
        for (int i = 0; i < 10; i++) {
            request(freshEmail(), ip).then().statusCode(202);
        }

        Response refused = request(freshEmail(), ip);

        refused.then().statusCode(429).body("code", equalTo("AUTH-012")).header("Retry-After", notNullValue());
        assertThat(Integer.parseInt(refused.header("Retry-After"))).isBetween(1, 3601);
        // Another address is not affected.
        request(freshEmail(), freshIp()).then().statusCode(202);
    }

    @Test
    void twentyFirstVerificationAndTwentyFirstConfirmationFromOneAddressAreRefusedWith429() {
        String ip = freshIp();
        for (int i = 0; i < 20; i++) {
            verify(freshEmail(), "12345678", ip).then().statusCode(400);
        }
        verify(freshEmail(), "12345678", ip).then().statusCode(429).body("code", equalTo("AUTH-012")).header("Retry-After", notNullValue());

        String other = freshIp();
        for (int i = 0; i < 20; i++) {
            confirm(java.util.UUID.randomUUID() + "." + "a".repeat(43), NEW_PASSWORD, other).then().statusCode(400);
        }
        confirm(java.util.UUID.randomUUID() + "." + "a".repeat(43), NEW_PASSWORD, other)
                .then().statusCode(429).body("code", equalTo("AUTH-012"));
    }

    @Test
    void fiveWrongCodesSpendTheCodeSoEvenTheRightOneStopsWorking() {
        String email = freshEmail();
        account(email);
        request(email, freshIp()).then().statusCode(202);
        String right = codeSentTo(email);
        String wrong = right.equals("00000000") ? "00000001" : "00000000";
        String ip = freshIp();

        for (int i = 0; i < 5; i++) {
            verify(email, wrong, ip).then().statusCode(400).body("code", equalTo("AUTH-011"));
        }

        verify(email, right, ip).then().statusCode(400).body("code", equalTo("AUTH-011"));
        assertThat(loginStatus(email, STRONG)).isEqualTo(200); // nothing changed, the account is not locked
    }

    @Test
    void anExpiredCodeACodeForAnotherAccountAndAnUnknownEmailAreRefusedWithTheSameStatusAndBody() {
        String email = freshEmail();
        account(email);
        String other = freshEmail();
        account(other);
        request(email, freshIp()).then().statusCode(202);
        String code = codeSentTo(email);

        // The right code, but for another account; and for an email that does not exist.
        Response otherAccount = verify(other, code, freshIp());
        Response unknown = verify(freshEmail(), code, freshIp());
        otherAccount.then().statusCode(400).body("code", equalTo("AUTH-011"));
        assertThat(unknown.statusCode()).isEqualTo(400);
        assertThat(unknown.jsonPath().getString("code")).isEqualTo("AUTH-011");
        assertThat(unknown.jsonPath().getString("message")).isEqualTo(otherAccount.jsonPath().getString("message"));

        jdbc.update("UPDATE password_reset_code SET expires_at = now() - interval '1 minute'");
        verify(email, code, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(401);
    }

    @Test
    void aTokenIsStoredOnlyAsAnHmacExpiresAfterTenMinutesAndAFakeOrTamperedOneIsRefused() {
        String email = freshEmail();
        account(email);
        String token = tokenFor(email);
        String secret = token.substring(token.indexOf('.') + 1);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_token WHERE token_hmac = ?", Integer.class, secret)).isZero();
        assertThat(jdbc.queryForObject("SELECT length(token_hmac) FROM password_reset_token WHERE id = ?::uuid", Integer.class,
                        token.substring(0, token.indexOf('.'))))
                .isEqualTo(64);
        // Right id, wrong secret; a random id; garbage.
        confirm(token.substring(0, token.indexOf('.')) + "." + "x".repeat(43), NEW_PASSWORD, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
        confirm(java.util.UUID.randomUUID() + "." + secret, NEW_PASSWORD, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
        confirm("basura", NEW_PASSWORD, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));

        jdbc.update("UPDATE password_reset_token SET expires_at = now() - interval '1 minute'");
        confirm(token, NEW_PASSWORD, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(401);
    }

    @Test
    void aSecondVerificationReplacesTheTokenSoOnlyTheNewestWorks() {
        String email = freshEmail();
        StaffUser user = account(email);
        String first = tokenFor(email);
        ageRequests();
        String second = tokenFor(email);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM password_reset_token WHERE staff_user_id = ?::uuid AND consumed_at IS NULL",
                        Integer.class,
                        user.getId().toString()))
                .isEqualTo(1);
        confirm(first, NEW_PASSWORD, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
        confirm(second, NEW_PASSWORD, freshIp()).then().statusCode(204);
    }

    @Test
    void aTokenOnlyWorksForItsOwnAccountAndPasswordOfTheOtherIsUntouched() {
        String email = freshEmail();
        account(email);
        String other = freshEmail();
        account(other);
        String token = tokenFor(email);

        confirm(token, NEW_PASSWORD, freshIp()).then().statusCode(204);

        assertThat(loginStatus(email, NEW_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(other, STRONG)).isEqualTo(200);
    }

    @Test
    void aNewRequestInvalidatesTheEarlierCodeAndLeavesOneLiveCode() {
        String email = freshEmail();
        StaffUser user = account(email);
        request(email, freshIp()).then().statusCode(202);
        String first = codeSentTo(email);
        ageRequests();
        request(email, freshIp()).then().statusCode(202);
        await().atMost(5, TimeUnit.SECONDS).until(() -> codesSent() == 2);
        String second = fake.lastCodeFor(email).orElseThrow();

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM password_reset_code WHERE staff_user_id = ?::uuid AND consumed_at IS NULL",
                        Integer.class,
                        user.getId().toString()))
                .isEqualTo(1);
        if (!first.equals(second)) {
            verify(email, first, freshIp()).then().statusCode(400);
        }
        verify(email, second, freshIp()).then().statusCode(200);
    }

    @Test
    void aWeakPasswordOrTheCurrentOneIsRefusedWithoutSpendingTheToken() {
        String email = freshEmail();
        account(email);
        String token = tokenFor(email);

        confirm(token, "corta", freshIp()).then().statusCode(400).body("code", equalTo("AUTH-007"));
        confirm(token, STRONG, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-007"));
        confirm(token, NEW_PASSWORD, freshIp()).then().statusCode(204);
    }

    @Test
    void changingThePasswordSpendsACodeRequestedAfterTheVerification() {
        String email = freshEmail();
        account(email);
        String token = tokenFor(email);
        ageRequests();
        request(email, freshIp()).then().statusCode(202);
        await().atMost(5, TimeUnit.SECONDS).until(() -> codesSent() == 2);
        String lateCode = fake.lastCodeFor(email).orElseThrow();

        confirm(token, NEW_PASSWORD, freshIp()).then().statusCode(204);

        verify(email, lateCode, freshIp()).then().statusCode(400).body("code", equalTo("AUTH-011"));
    }

    @Test
    void twoSimultaneousConfirmationsOfTheSameTokenLetExactlyOneWin() throws Exception {
        String email = freshEmail();
        account(email);
        String code = tokenFor(email);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (String password : List.of(NEW_PASSWORD, "una-clave-larga-pero-de-otra-persona")) {
                String ip = freshIp();
                results.add(pool.submit(() -> {
                    start.await();
                    return confirm(code, password, ip).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(204, 400);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoSimultaneousVerificationsOfTheSameCodeIssueExactlyOneToken() throws Exception {
        String email = freshEmail();
        StaffUser user = account(email);
        request(email, freshIp()).then().statusCode(202);
        String code = codeSentTo(email);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                String ip = freshIp();
                results.add(pool.submit(() -> {
                    start.await();
                    return verify(email, code, ip).statusCode();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 400);
            assertThat(jdbc.queryForObject(
                            "SELECT count(*) FROM password_reset_token WHERE staff_user_id = ?::uuid AND consumed_at IS NULL",
                            Integer.class,
                            user.getId().toString()))
                    .isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aProviderThatIsDownNeverChangesTheAnswer() {
        String email = freshEmail();
        account(email);
        fake.failing(true);
        Integer sentBefore = jdbc.queryForObject("SELECT count(*) FROM password_reset_request WHERE sent", Integer.class);

        Response response = request(email, freshIp());

        // Nothing was delivered and the caller cannot tell: same status, same (empty) body.
        response.then().statusCode(202);
        assertThat(response.asString()).isEmpty();
        await().during(1, TimeUnit.SECONDS).atMost(3, TimeUnit.SECONDS).until(() -> fake.sent().isEmpty());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM password_reset_request WHERE sent", Integer.class))
                .isEqualTo(sentBefore);
    }

    @Test
    void whenTheDailyCapOfEightySendsIsReachedTheAnswerIsStillTheSameAndNothingIsSent() {
        String email = freshEmail();
        account(email);
        for (int i = 0; i < 80; i++) {
            jdbc.update(
                    "INSERT INTO password_reset_request (id, kind, email_hmac, ip_hmac, requested_at, accepted, sent)"
                            + " VALUES (?, 'REQUEST', 'x', 'x', now(), true, true)",
                    java.util.UUID.randomUUID());
        }

        try {
            request(email, freshIp()).then().statusCode(202);

            assertThat(fake.sent()).isEmpty();
        } finally {
            jdbc.update("DELETE FROM password_reset_request WHERE email_hmac = 'x'");
        }
    }

    @Test
    void changingYourOwnPasswordAndAnAdminResetBothSendTheNotice() {
        String email = freshEmail();
        StaffUser user = account(email);
        String token = given().contentType("application/json")
                .body(Map.of("email", email, "password", STRONG))
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("accessToken");

        given().header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .body(Map.of("currentPassword", STRONG, "newPassword", NEW_PASSWORD))
                .post("/auth/me/password")
                .then()
                .statusCode(204);
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> fake.sent().stream().filter(s -> s.kind().equals("password_changed")).count() == 1);

        String adminEmail = freshEmail();
        staffUsers.save(StaffUser.create(adminEmail, passwordEncoder.encode(STRONG), "Admin", StaffRole.ADMIN, false));
        String adminToken = given().contentType("application/json")
                .body(Map.of("email", adminEmail, "password", STRONG))
                .post("/auth/login")
                .then()
                .extract()
                .path("accessToken");
        given().header("Authorization", "Bearer " + adminToken)
                .contentType("application/json")
                .body(Map.of("newPassword", "una-clave-restablecida-por-admin"))
                .post("/auth/staff-users/" + user.getId() + "/password")
                .then()
                .statusCode(204);
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> fake.sent().stream().filter(s -> s.kind().equals("password_changed")).count() == 2);
        assertThat(fake.sent().stream().map(FakeEmailSender.Sent::to)).containsOnly(email);
    }

    @Test
    void theAnswerTakesAboutAsLongForAnExistingAndForAnUnknownAccount() {
        List<Long> existing = new ArrayList<>();
        List<Long> unknown = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String email = freshEmail();
            account(email);
            long start = System.nanoTime();
            request(email, freshIp()).then().statusCode(202);
            existing.add((System.nanoTime() - start) / 1_000_000);
            start = System.nanoTime();
            request(freshEmail(), freshIp()).then().statusCode(202);
            unknown.add((System.nanoTime() - start) / 1_000_000);
        }

        long difference = Math.abs(median(existing) - median(unknown));
        assertThat(difference).as("median ms existing %s vs unknown %s", median(existing), median(unknown)).isLessThan(250);
    }

    @Test
    void aWrongCodeTakesAboutAsLongForAnExistingAndForAnUnknownAccount() {
        List<Long> existing = new ArrayList<>();
        List<Long> unknown = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            String email = freshEmail();
            account(email);
            request(email, freshIp()).then().statusCode(202);
            String right = codeSentTo(email);
            String wrong = right.equals("00000000") ? "00000001" : "00000000";
            long start = System.nanoTime();
            verify(email, wrong, freshIp()).then().statusCode(400);
            existing.add((System.nanoTime() - start) / 1_000_000);
            start = System.nanoTime();
            verify(freshEmail(), wrong, freshIp()).then().statusCode(400);
            unknown.add((System.nanoTime() - start) / 1_000_000);
        }

        long difference = Math.abs(median(existing) - median(unknown));
        assertThat(difference).as("median ms existing %s vs unknown %s", median(existing), median(unknown)).isLessThan(250);
    }

    private static long median(List<Long> values) {
        List<Long> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    @Test
    void nothingTheRequestWritesHoldsTheAddressOrTheEmailInTheClear() {
        String email = freshEmail();
        account(email);
        String ip = freshIp();

        request(email, ip).then().statusCode(202);

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM password_reset_request WHERE ip_hmac = ? OR email_hmac = ?", Integer.class, ip, email))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM password_reset_request WHERE length(ip_hmac) = 64 AND length(email_hmac) = 64",
                        Integer.class))
                .isPositive();
    }
}

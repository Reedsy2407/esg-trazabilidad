package pe.esgtrazabilidad.auth.it;

import java.time.Duration;
import java.util.List;

import io.restassured.response.Response;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import pe.esgtrazabilidad.auth.it.support.RecyclerApiIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * CA-7 with small limits so the tests stay short: 3 failures per code (blocked 15 minutes from the last),
 * 5 attempts per address in a 15 minute window. Callers are told apart by X-Forwarded-For, and time
 * moves with the controlled clock, never by waiting.
 */
@TestPropertySource(
        properties = {
            "esg.auth.recycler-activation.max-failed-attempts-per-code=3",
            "esg.auth.recycler-activation.code-block-minutes=15",
            "esg.auth.recycler-activation.max-attempts-per-address=5",
            "esg.auth.recycler-activation.address-window-minutes=15"
        })
class RecyclerActivationLimitsIT extends RecyclerApiIT {

    @Test
    void aCodeThatFailedThreeTimesIsBlockedWith429AndLiftsFifteenMinutesAfterTheLastFailure() {
        String code = unissuedCode();
        for (int i = 0; i < 3; i++) {
            activate(code, "install-1", uniqueAddress()).then().statusCode(400).body("code", equalTo("AUTH-014"));
            clock.advance(Duration.ofMinutes(1));
        }

        Response blocked = activate(code, "install-1", uniqueAddress());
        blocked.then().statusCode(429).body("code", equalTo("AUTH-015"));
        long retryAfter = Long.parseLong(blocked.header("Retry-After"));
        assertThat(retryAfter).isBetween(1L, Duration.ofMinutes(15).toSeconds());

        // A refused attempt is not counted, so hammering does not push the end of the block further.
        clock.advance(Duration.ofMinutes(13));
        activate(code, "install-1", uniqueAddress()).then().statusCode(429);
        clock.advance(Duration.ofMinutes(2).plusSeconds(1)); // 3 failures + 1 + 13 + 2 min: past 15 min from the last
        activate(code, "install-1", uniqueAddress()).then().statusCode(400).body("code", equalTo("AUTH-014"));
    }

    @Test
    void failuresSpacedMoreThanTheBlockWindowApartNeverBlock() {
        String code = unissuedCode();
        for (int i = 0; i < 6; i++) {
            activate(code, "install-spaced", uniqueAddress()).then().statusCode(400);
            clock.advance(Duration.ofMinutes(16));
        }
    }

    @Test
    void theBlockOfOneCodeDoesNotAffectAnother() {
        String blockedCode = unissuedCode();
        for (int i = 0; i < 3; i++) {
            activate(blockedCode, "install-2", uniqueAddress()).then().statusCode(400);
        }
        activate(blockedCode, "install-2", uniqueAddress()).then().statusCode(429);

        IssuedAccount other = issueAccount();
        activate(other.code(), "install-3", uniqueAddress()).then().statusCode(200);
    }

    @Test
    void anAddressThatMadeFiveAttemptsIsRefusedWith429ThenOtherAddressesAndLaterTimeAreFine() {
        String address = uniqueAddress();
        for (int i = 0; i < 5; i++) {
            // A different wrong code each time, so only the address limit can be what stops it.
            activate(unissuedCode(), "install-4", address).then().statusCode(400);
        }

        Response refused = activate(unissuedCode(), "install-4", address);
        refused.then().statusCode(429).body("code", equalTo("AUTH-015"));
        assertThat(Long.parseLong(refused.header("Retry-After"))).isPositive();
        // Even a real code is refused from that address while it is over the limit.
        IssuedAccount issued = issueAccount();
        activate(issued.code(), "install-4", address).then().statusCode(429);

        activate(issued.code(), "install-5", uniqueAddress()).then().statusCode(200);

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        activate(unissuedCode(), "install-4", address).then().statusCode(400).body("code", equalTo("AUTH-014"));
    }

    @Test
    void malformedCodesCountAgainstTheAddressToo() {
        String address = uniqueAddress();
        for (int i = 0; i < 5; i++) {
            activate("x" + i, "install-6", address).then().statusCode(400);
        }

        activate("otro", "install-6", address).then().statusCode(429);
    }

    @Test
    void theTablesHoldOnlyHmacsNeverAnAddressOrACode() {
        String address = uniqueAddress();
        String code = unissuedCode();
        activate(code, "install-7", address).then().statusCode(400);

        List<String> keys = jdbc.queryForList("select key_hmac from recycler_activation_attempt", String.class);
        assertThat(keys).isNotEmpty().allMatch(key -> key.matches("[0-9a-f]{64}"));
        assertThat(keys).doesNotContain(address, code);
    }
}

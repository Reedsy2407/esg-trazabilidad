package pe.esgtrazabilidad.recycler.it.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;

/**
 * Single place every RestAssured-based IT configures RestAssured's static
 * state from, in its own @BeforeEach.
 *
 * Why this exists: `new RequestSpecBuilder()` does NOT start from a blank
 * spec -- its constructor snapshots whatever RestAssured.requestSpecification
 * (and RestAssured.port) currently holds and uses it as the base spec. Failsafe
 * runs every IT class in one JVM, so assigning a freshly built spec in each
 * @BeforeEach without clearing first wraps the previous test's spec, test
 * after test and class after class: requests end up carrying a pile of
 * Authorization headers (Spring's bearer resolver reads the first, i.e. the
 * oldest, eventually expired one) and a port from an earlier class's cached
 * server. RestAssured.reset() first makes each class start from scratch.
 */
public final class RestAssuredSetup {

    private RestAssuredSetup() {
    }

    /** Every request carries a fresh valid token by default. */
    public static void authenticated(int port) {
        anonymous(port);
        RestAssured.requestSpecification = new RequestSpecBuilder()
                .addHeader("Authorization", "Bearer " + TestJwtTokens.validToken())
                .build();
    }

    /** No default spec at all -- each test sets (or deliberately omits) its own Authorization header. */
    public static void anonymous(int port) {
        RestAssured.reset();
        RestAssured.port = port;
    }
}

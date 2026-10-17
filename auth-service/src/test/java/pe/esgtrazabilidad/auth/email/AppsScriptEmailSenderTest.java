package pe.esgtrazabilidad.auth.email;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The adapter against a local server that imitates the Apps Script web app: POST /exec answers
 * 302 with a Location, and the GET of that Location answers the result as JSON. Nothing here
 * reaches Google.
 */
class AppsScriptEmailSenderTest {

    private static final String SECRET = "un-secreto-compartido-de-prueba-de-32-caracteres";
    private static final String RECIPIENT = "ana.perez@esgtrazabilidad.pe";
    private static final String CODE = "04271983";

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>(); // "POST /exec <body>" or "GET /result"
    private volatile String locationHeader = "/result";
    private volatile int postStatus = 302;
    private volatile String resultBody = "{\"ok\":true}";
    private volatile int resultStatus = 200;
    private volatile String resultLocation = null;
    private volatile long resultDelayMillis = 0;
    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/exec", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add("POST /exec " + body);
            if (locationHeader != null) {
                exchange.getResponseHeaders().add("Location", locationHeader);
            }
            exchange.sendResponseHeaders(postStatus, -1);
            exchange.close();
        });
        server.createContext("/result", exchange -> {
            requests.add("GET /result");
            sleep(resultDelayMillis);
            reply(exchange, resultStatus, resultBody, resultLocation);
        });
        server.start();

        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger("esg.auth.email")).addAppender(logs);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        ((Logger) LoggerFactory.getLogger("esg.auth.email")).detachAppender(logs);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void reply(HttpExchange exchange, int status, String body, String location) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        if (location != null) {
            exchange.getResponseHeaders().add("Location", location);
        }
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private String base() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Local server, redirects limited to it (the real constructor allows Google hosts over https only). */
    private AppsScriptEmailSender sender(Duration readTimeout) {
        Predicate<URI> localOnly = uri -> "127.0.0.1".equals(uri.getHost());
        return new AppsScriptEmailSender(URI.create(base() + "/exec"), SECRET, Duration.ofSeconds(2), readTimeout, localOnly);
    }

    private AppsScriptEmailSender sender() {
        return sender(Duration.ofSeconds(3));
    }

    private String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    private void assertNoSensitiveDataInLogs() {
        assertThat(allLogs())
                .doesNotContain(RECIPIENT)
                .doesNotContain(CODE)
                .doesNotContain(SECRET)
                .doesNotContain("127.0.0.1")
                .doesNotContain("/exec");
    }

    private static void assertReason(Throwable thrown, String reason) {
        assertThat(thrown).isInstanceOf(EmailSendException.class);
        assertThat(((EmailSendException) thrown).getReason()).isEqualTo(reason);
    }

    @Test
    void postsOnceThenFollowsTheRedirectWithAGetAndNeverResendsThePost() {
        sender().sendResetCode(RECIPIENT, CODE);

        assertThat(requests).hasSize(2);
        assertThat(requests.get(0)).startsWith("POST /exec ");
        assertThat(requests.get(1)).isEqualTo("GET /result");
        String body = requests.get(0).substring("POST /exec ".length());
        assertThat(body)
                .contains("\"secret\":\"" + SECRET + "\"")
                .contains("\"type\":\"reset_code\"")
                .contains("\"to\":\"" + RECIPIENT + "\"")
                .contains("\"code\":\"" + CODE + "\"");
        assertThat(allLogs()).contains("event=email_sent provider=appsscript durationMs=");
        assertNoSensitiveDataInLogs();
    }

    @Test
    void thePasswordChangedNoticeCarriesTheLimaTimeInTheShapeTheScriptAccepts() {
        sender().sendPasswordChanged(RECIPIENT, Instant.parse("2026-10-16T19:05:00Z"));

        String body = requests.get(0);
        assertThat(body).contains("\"type\":\"password_changed\"").contains("\"when\":\"16/10/2026 14:05 hora de Lima\"");
        assertThat(body).doesNotContain("\"code\"");
        // The script's own check for "when": letters, digits, space, / : - , . and at most 40 characters.
        assertThat("16/10/2026 14:05 hora de Lima").matches("[0-9A-Za-z :/\\-,.]{1,40}");
    }

    @Test
    void aPostAnsweredWithAnEmptyBodyAndNoRedirectIsMalformedNotSuccess() {
        postStatus = 200;
        locationHeader = null;

        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_response"));
    }

    @Test
    void aRedirectToAnotherHostIsNeverFollowed() {
        locationHeader = "http://203.0.113.9/result";

        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_redirect"));

        assertThat(requests).hasSize(1); // only the POST
        assertNoSensitiveDataInLogs();
    }

    @Test
    void theRealConstructorFollowsGoogleHostsOverHttpsOnly() {
        for (String location : List.of(
                "http://script.google.com/macros/echo",
                "https://evil.example/macros/echo",
                "https://script.google.com.evil.example/x",
                "https://user@script.google.com/x",
                "https://script.google.com:8443/x",
                "ftp://script.google.com/x")) {
            requests.clear();
            locationHeader = location;
            AppsScriptEmailSender real = new AppsScriptEmailSender(URI.create(base() + "/exec"), SECRET);

            assertThatThrownBy(() -> real.sendResetCode(RECIPIENT, CODE))
                    .as(location)
                    .satisfies(t -> assertReason(t, "bad_redirect"));
            assertThat(requests).as(location).hasSize(1);
        }
    }

    @Test
    void aRedirectWithoutALocationFails() {
        locationHeader = null;

        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_redirect"));
    }

    @Test
    void moreThanOneRedirectFails() {
        resultStatus = 302;
        resultBody = "";
        resultLocation = "/result";

        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "too_many_redirects"));

        assertThat(requests).hasSize(2); // the POST and one GET, never a third request
    }

    @Test
    void theScriptsOwnRefusalsBecomeFixedReasons() {
        for (String reason : List.of("quota", "unauthorized", "bad_recipient", "bad_code", "error")) {
            resultBody = "{\"ok\":false,\"error\":\"" + reason + "\"}";
            assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, reason));
        }
        resultBody = "{\"ok\":false,\"error\":\"algo con " + RECIPIENT + " dentro\"}";
        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "script_error"));

        assertThat(allLogs()).contains("reason=quota").contains("reason=unauthorized").contains("reason=script_error");
        assertNoSensitiveDataInLogs();
    }

    @Test
    void aServerErrorAMalformedBodyAndAnOversizedBodyFail() {
        resultStatus = 500;
        resultBody = "{\"ok\":true}";
        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "http_500"));

        resultStatus = 200;
        resultBody = "<html>login</html>";
        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_response"));

        resultBody = "{\"nothing\":1}";
        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_response"));

        resultBody = "{\"ok\":true,\"pad\":\"" + "x".repeat(9000) + "\"}";
        assertThatThrownBy(() -> sender().sendResetCode(RECIPIENT, CODE)).satisfies(t -> assertReason(t, "bad_response"));
        assertNoSensitiveDataInLogs();
    }

    @Test
    void aSlowScriptTimesOutAndIsNotRetried() {
        resultDelayMillis = 1500;

        assertThatThrownBy(() -> sender(Duration.ofMillis(300)).sendResetCode(RECIPIENT, CODE))
                .satisfies(t -> assertReason(t, "timeout"));

        assertThat(requests.stream().filter(r -> r.startsWith("POST")).count()).isEqualTo(1);
        assertThat(allLogs()).contains("reason=timeout");
        assertNoSensitiveDataInLogs();
    }

    @Test
    void anUnreachableServerFailsWithAFixedReason() {
        AppsScriptEmailSender unreachable = new AppsScriptEmailSender(
                URI.create("http://127.0.0.1:1/exec"), SECRET, Duration.ofSeconds(1), Duration.ofSeconds(1), uri -> false);

        assertThatThrownBy(() -> unreachable.sendResetCode(RECIPIENT, CODE))
                .satisfies(t -> assertThat(((EmailSendException) t).getReason()).isIn("io_error", "timeout"));
        assertNoSensitiveDataInLogs();
    }
}

package pe.esgtrazabilidad.auth.email;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sends through a Google Apps Script published as a web app (the script sends from the project's
 * Gmail account). The script answers a POST with a 302 whose Location holds the result already
 * computed, so the redirect is followed by hand with a GET: re-sending the POST would run the
 * script again and send the email twice. The Location is checked before it is followed (https and
 * a Google host only, so a tampered answer cannot point this service at another server) and at most
 * one redirect is followed. No retries, for the same reason. The body carries the shared secret
 * (the script cannot read request headers) and a fixed template name: the script owns the texts.
 *
 * <p>Logs carry the provider, the duration and a fixed reason code, never the recipient, the code,
 * the secret, the URL or anything from the answer.
 */
public class AppsScriptEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger("esg.auth.email");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ALLOWED_HOSTS = Set.of("script.google.com", "script.googleusercontent.com");
    // The reasons the script itself can answer; anything else is logged as script_error.
    private static final Set<String> SCRIPT_REASONS =
            Set.of("unauthorized", "quota", "bad_request", "bad_recipient", "bad_code", "bad_when", "bad_type", "error");
    private static final int MAX_RESPONSE_BYTES = 8192;
    // Letters, digits, spaces, "/" and ":" only: what the script accepts in "when".
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'hora de Lima'", Locale.ROOT).withZone(ZoneId.of("America/Lima"));

    private final HttpClient client;
    private final URI webhook;
    private final String secret;
    private final Duration readTimeout;
    private final Predicate<URI> redirectAllowed;

    /** The real thing: connect 3 s, read 10 s (a cold script can take seconds), Google hosts only. */
    public AppsScriptEmailSender(URI webhook, String secret) {
        this(webhook, secret, Duration.ofSeconds(3), Duration.ofSeconds(10), AppsScriptEmailSender::isGoogleHttps);
    }

    /** Tests point this at a local server and widen the redirect check to it. */
    AppsScriptEmailSender(
            URI webhook, String secret, Duration connectTimeout, Duration readTimeout, Predicate<URI> redirectAllowed) {
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(connectTimeout)
                .build();
        this.webhook = webhook;
        this.secret = secret;
        this.readTimeout = readTimeout;
        this.redirectAllowed = redirectAllowed;
    }

    @Override
    public void sendResetCode(String to, String code) {
        Map<String, String> payload = payload("reset_code", to);
        payload.put("code", code);
        send(payload);
    }

    @Override
    public void sendPasswordChanged(String to, Instant when) {
        Map<String, String> payload = payload("password_changed", to);
        payload.put("when", WHEN.format(when));
        send(payload);
    }

    private Map<String, String> payload(String type, String to) {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("secret", secret);
        payload.put("type", type);
        payload.put("to", to);
        return payload;
    }

    private void send(Map<String, String> payload) {
        long start = System.nanoTime();
        try {
            deliver(payload);
        } catch (EmailSendException failure) {
            log.warn(
                    "event=email_failed provider=appsscript durationMs={} reason={}",
                    millisSince(start),
                    failure.getReason());
            throw failure;
        }
        log.info("event=email_sent provider=appsscript durationMs={}", millisSince(start));
    }

    private void deliver(Map<String, String> payload) {
        try {
            HttpRequest post = HttpRequest.newBuilder(webhook)
                    .timeout(readTimeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(MAPPER.writeValueAsBytes(payload)))
                    .build();
            HttpResponse<byte[]> response = client.send(post, HttpResponse.BodyHandlers.ofByteArray());
            if (isRedirect(response.statusCode())) {
                HttpRequest get = HttpRequest.newBuilder(redirectTarget(response))
                        .timeout(readTimeout)
                        .GET()
                        .build();
                response = client.send(get, HttpResponse.BodyHandlers.ofByteArray());
                if (isRedirect(response.statusCode())) {
                    throw new EmailSendException("too_many_redirects");
                }
            }
            if (response.statusCode() != 200) {
                throw new EmailSendException("http_" + response.statusCode());
            }
            checkAnswer(response.body());
        } catch (HttpTimeoutException timeout) {
            throw new EmailSendException("timeout");
        } catch (JsonProcessingException malformed) {
            throw new EmailSendException("bad_response");
        } catch (IOException failure) {
            throw new EmailSendException("io_error");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new EmailSendException("interrupted");
        }
    }

    private URI redirectTarget(HttpResponse<byte[]> response) {
        String location = response.headers().firstValue("Location").orElse(null);
        if (location == null) {
            throw new EmailSendException("bad_redirect");
        }
        URI target;
        try {
            target = webhook.resolve(location);
        } catch (IllegalArgumentException malformed) {
            throw new EmailSendException("bad_redirect");
        }
        if (!redirectAllowed.test(target)) {
            throw new EmailSendException("bad_redirect");
        }
        return target;
    }

    /** The script always answers HTTP 200; the outcome is in the body: {"ok":true} or {"ok":false,"error":"..."}. */
    private void checkAnswer(byte[] body) throws IOException {
        if (body.length == 0 || body.length > MAX_RESPONSE_BYTES) {
            throw new EmailSendException("bad_response");
        }
        JsonNode answer = MAPPER.readTree(new String(body, StandardCharsets.UTF_8));
        if (answer == null || !answer.has("ok")) {
            throw new EmailSendException("bad_response");
        }
        if (!answer.get("ok").asBoolean(false)) {
            String error = answer.path("error").asText("");
            throw new EmailSendException(SCRIPT_REASONS.contains(error) ? error : "script_error");
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static boolean isGoogleHttps(URI uri) {
        return "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
                && uri.getUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    private static long millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

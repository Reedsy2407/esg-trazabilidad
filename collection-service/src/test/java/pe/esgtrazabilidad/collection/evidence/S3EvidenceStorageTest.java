package pe.esgtrazabilidad.collection.evidence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * The real adapter (AWS SDK v2, path-style, three timeouts) against a local stub server that speaks just
 * enough HTTP: no internet, no credentials of any kind. It checks what the adapter sends, what the signed
 * URLs promise, and that a slow bucket is cut by the timeouts instead of hanging.
 */
class S3EvidenceStorageTest {

    private static final String BUCKET = "test-bucket";
    private static final String KEY = "evidence/11111111-1111-1111-1111-111111111111/2026/22222222-2222-2222-2222-222222222222.jpg";
    private static final String SHA256 = "ab".repeat(32);

    private HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Long> stored = new ConcurrentHashMap<>();
    private final CountDownLatch releaseSlowCalls = new CountDownLatch(1);
    private volatile boolean hang;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void stopStub() {
        releaseSlowCalls.countDown();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
        try {
            if (hang) {
                releaseSlowCalls.await(30, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
        Long size = stored.get(path);
        switch (exchange.getRequestMethod()) {
            case "HEAD" -> {
                if (size == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    exchange.getResponseHeaders().add("Content-Length", Long.toString(size));
                    exchange.sendResponseHeaders(200, -1);
                }
            }
            case "DELETE" -> {
                stored.remove(path);
                exchange.sendResponseHeaders(204, -1);
            }
            default -> exchange.sendResponseHeaders(405, -1);
        }
        exchange.close();
    }

    private S3EvidenceStorage storage(long connectSeconds, long readSeconds, long callSeconds) {
        String endpoint = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        return (S3EvidenceStorage) new EvidenceConfig()
                .evidenceStorage(endpoint, "us-east-005", BUCKET, "stub-id", "stub-secret", connectSeconds, readSeconds, 1, callSeconds);
    }

    @Test
    void headReportsTheSizeOfAnExistingObjectAndNothingForAMissingOne() {
        S3EvidenceStorage storage = storage(2, 5, 10);
        stored.put("/" + BUCKET + "/" + KEY, 150_000L);

        assertThat(storage.head(KEY)).contains(new EvidenceStorage.StoredObject(150_000));
        assertThat(storage.head(KEY + ".missing")).isEqualTo(Optional.empty());
        assertThat(requests).contains("HEAD /" + BUCKET + "/" + KEY);
    }

    @Test
    void deleteRemovesTheObjectAndDeletingAgainIsNotAnError() {
        S3EvidenceStorage storage = storage(2, 5, 10);
        stored.put("/" + BUCKET + "/" + KEY, 10L);

        storage.delete(KEY);
        storage.delete(KEY);

        assertThat(stored).isEmpty();
        assertThat(requests).containsExactly("DELETE /" + BUCKET + "/" + KEY, "DELETE /" + BUCKET + "/" + KEY);
    }

    @Test
    void theUploadUrlSignsTheSizeTheTypeAndTheHashAndExpiresWhenAsked() {
        S3EvidenceStorage storage = storage(2, 5, 10);

        URI url = URI.create(storage.presignUpload(KEY, 150_000, SHA256, Duration.ofMinutes(10)));
        String query = url.getQuery();

        assertThat(url.getPath()).isEqualTo("/" + BUCKET + "/" + KEY);
        assertThat(query).contains("X-Amz-Expires=600").contains("X-Amz-Signature=").contains("X-Amz-Algorithm=AWS4-HMAC-SHA256");
        String signedHeaders = URLDecoder.decode(
                java.util.Arrays.stream(query.split("&")).filter(p -> p.startsWith("X-Amz-SignedHeaders=")).findFirst().orElse(""),
                StandardCharsets.UTF_8).toLowerCase();
        assertThat(signedHeaders).contains("host").contains("content-type").contains("content-length").contains("x-amz-checksum-sha256");
        assertThat(query.toLowerCase()).as("no extra checksum algorithm is imposed on the uploader").doesNotContain("crc32");
        // The credential id appears in the URL by design; the secret never does.
        assertThat(url.toString()).doesNotContain("stub-secret");
    }

    @Test
    void theReadUrlIsAGetOfTheSameObjectThatExpiresInFiveMinutes() {
        S3EvidenceStorage storage = storage(2, 5, 10);

        URI url = URI.create(storage.presignRead(KEY, Duration.ofMinutes(5)));

        assertThat(url.getPath()).isEqualTo("/" + BUCKET + "/" + KEY);
        assertThat(url.getQuery()).contains("X-Amz-Expires=300").contains("X-Amz-Signature=");
        assertThat(url.getScheme() + "://" + url.getHost()).as("the stub, not a public host").startsWith("http://127.0.0.1");
    }

    @Test
    void aBucketThatDoesNotAnswerIsCutByTheTimeoutsInsteadOfHanging() {
        S3EvidenceStorage storage = storage(1, 1, 3);
        hang = true;

        long started = System.nanoTime();
        assertThatThrownBy(() -> storage.head(KEY)).isInstanceOf(RuntimeException.class);
        long elapsedSeconds = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - started);

        assertThat(elapsedSeconds).as("the whole-call ceiling of 3 s bounds the wait").isLessThanOrEqualTo(8);
    }
}

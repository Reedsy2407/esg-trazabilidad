package pe.esgtrazabilidad.reporting.sunat.adapter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Decolecta adapter against a local stub that replays recorded shapes (the
 * documented 200 body, the documented 422, and the failure modes). Nothing here
 * reaches the real provider.
 */
class DecolectaRucLookupTest {

    private static final Ruc RUC = Ruc.of("20131312955");
    private static final String OK_BODY = """
            {"razon_social":"EMPRESA DE PRUEBA S.A.C.","numero_documento":"20131312955","estado":"ACTIVO",
             "condicion":"HABIDO","direccion":"AV. EJEMPLO 123","ubigeo":"150131","distrito":"SAN ISIDRO",
             "provincia":"LIMA","departamento":"LIMA","es_agente_retencion":false,"locales_anexos":null}
            """;

    private HttpServer server;
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> requestedUri = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = OK_BODY;
    private volatile long delayMillis = 0;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestedUri.set(exchange.getRequestURI().toString());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private DecolectaRucLookup adapter(Duration readTimeout) {
        return new DecolectaRucLookup(
                "http://127.0.0.1:" + server.getAddress().getPort(), "test-token", Duration.ofSeconds(2), readTimeout);
    }

    @Test
    void parsesTheDocumentedOkBodyAndSendsTheBearerToken() {
        RucLookupResult result = adapter(Duration.ofSeconds(2)).lookup(RUC);

        assertThat(result).isEqualTo(
                new RucLookupResult.Found("EMPRESA DE PRUEBA S.A.C.", "ACTIVO", "HABIDO", "150131"));
        assertThat(authorization.get()).isEqualTo("Bearer test-token");
        assertThat(requestedUri.get()).isEqualTo("/v1/sunat/ruc?numero=20131312955");
    }

    @Test
    void parsesTheRecordingOfARealProviderAnswer() throws IOException {
        try (var recording = getClass().getResourceAsStream("/sunat/decolecta-ruc-20131312955.json")) {
            body = new String(recording.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isEqualTo(new RucLookupResult.Found(
                "SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA - SUNAT",
                "ACTIVO",
                "HABIDO",
                "150101"));
    }

    @Test
    void theTokenIsNeverLoggedInAnyFailureMode() {
        Logger logger = (Logger) LoggerFactory.getLogger(DecolectaRucLookup.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            for (int failing : new int[] {401, 403, 429, 500}) {
                status = failing;
                body = "{\"error\":\"x\"}";
                adapter(Duration.ofSeconds(2)).lookup(RUC);
            }
            status = 200;
            body = "<html>gateway</html>";
            adapter(Duration.ofSeconds(2)).lookup(RUC);
            int deadPort = server.getAddress().getPort();
            server.stop(0);
            new DecolectaRucLookup("http://127.0.0.1:" + deadPort, "test-token", Duration.ofSeconds(1), Duration.ofSeconds(1))
                    .lookup(RUC);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).isNotEmpty();
        assertThat(appender.list).allSatisfy(event -> {
            assertThat(event.getFormattedMessage()).doesNotContain("test-token").doesNotContain("Bearer");
            assertThat(event.getThrowableProxy()).isNull();
        });
    }

    @Test
    void anUnknownRucAnswerIsNotFound() {
        status = 422;
        body = "{\"message\":\"ruc no valido\"}";

        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isInstanceOf(RucLookupResult.NotFound.class);

        status = 404;
        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isInstanceOf(RucLookupResult.NotFound.class);
    }

    @Test
    void serverErrorsBadTokenAndQuotaAreUnavailable() {
        for (int failing : new int[] {401, 403, 429, 500, 502, 503}) {
            status = failing;
            body = "{\"error\":\"x\"}";
            assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC))
                    .as("HTTP %d", failing)
                    .isInstanceOf(RucLookupResult.Unavailable.class);
        }
    }

    @Test
    void malformedOrIncompleteBodiesAreUnavailable() {
        body = "<html>gateway</html>";
        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);

        body = "{\"estado\":\"ACTIVO\"}";
        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);

        body = "";
        assertThat(adapter(Duration.ofSeconds(2)).lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);
    }

    @Test
    void aSlowProviderIsUnavailableAfterTheReadTimeout() {
        delayMillis = 1500;

        assertThat(adapter(Duration.ofMillis(300)).lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);
    }

    @Test
    void anUnreachableProviderIsUnavailable() {
        int deadPort = server.getAddress().getPort();
        server.stop(0);
        DecolectaRucLookup adapter = new DecolectaRucLookup(
                "http://127.0.0.1:" + deadPort, "test-token", Duration.ofSeconds(1), Duration.ofSeconds(1));

        assertThat(adapter.lookup(RUC)).isInstanceOf(RucLookupResult.Unavailable.class);
    }
}

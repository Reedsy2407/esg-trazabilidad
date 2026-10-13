package pe.esgtrazabilidad.reporting.sigersolmunicipal.adapter;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats.Answer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SIGERSOL adapter against a local stub that replays the two real responses recorded on
 * 2026-10-12 (La Molina 2024: one row; 2026: no rows) and the failure modes. Nothing here reaches
 * the real service.
 */
class HttpSigersolMunicipalTest {

    private HttpServer server;
    private HttpSigersolMunicipal adapter;
    private final AtomicReference<String> requestedPath = new AtomicReference<>();
    private final AtomicReference<String> requestedBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile byte[] body = new byte[0];
    private volatile String contentType = "application/json";
    private volatile long delayMillis = 0;

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestedPath.set(exchange.getRequestURI().getPath());
            requestedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        adapter = new HttpSigersolMunicipal(
                "http://127.0.0.1:" + server.getAddress().getPort() + "/srv-sigersol/rest",
                Duration.ofMillis(500), Duration.ofMillis(400));
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private static byte[] recording(String name) throws IOException {
        try (var in = HttpSigersolMunicipalTest.class.getResourceAsStream("/sigersol/" + name)) {
            return in.readAllBytes();
        }
    }

    @Test
    void theRecordedRowBecomesFoundAndTheRequestUsesThePairsOfTheUbigeo() throws IOException {
        body = recording("lista-recicladores-150114-2024.json");

        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Found(2, 31));
        assertThat(requestedPath.get()).isEqualTo("/srv-sigersol/rest/reporteValorizacion/listaRecicladores");
        assertThat(requestedBody.get()).contains("\"anio\":2024").contains("\"cod_departamento\":\"15\"")
                .contains("\"cod_provincia\":\"01\"").contains("\"cod_distrito\":\"14\"");
    }

    @Test
    void anEmptyListIsNoData() throws IOException {
        body = recording("lista-recicladores-150114-2026-sin-filas.json");

        assertThat(adapter.fetch(2026, "150114")).isEqualTo(new Answer.NoData());
    }

    @Test
    void theDeclaredCharsetIsRespected() {
        String json = "{\"exito\":true,\"mensajeUsuario\":\"Obtención\",\"datosAdicionales\":{\"LstRecicladores\":"
                + "[{\"distrito\":\"BREÑA\",\"cant_asoc_recicladores\":1,\"total_recicladores\":4}]}}";
        body = json.getBytes(Charset.forName("ISO-8859-1"));
        contentType = "application/json;charset=ISO-8859-1";

        assertThat(adapter.fetch(2024, "150105")).isEqualTo(new Answer.Found(1, 4));
    }

    @Test
    void anythingUnexpectedIsUnavailableAndNeverThrows() {
        status = 500;
        body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        status = 200;
        body = "<html>mantenimiento</html>".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        body = "{\"exito\":false,\"datosAdicionales\":{\"LstRecicladores\":[]}}".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        body = "{\"exito\":true,\"datosAdicionales\":{}}".getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());
    }

    @Test
    void rowsWithoutUsableCountsOrSeveralRowsAreUnavailable() {
        body = "{\"exito\":true,\"datosAdicionales\":{\"LstRecicladores\":[{\"cant_asoc_recicladores\":-1,\"total_recicladores\":4}]}}"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        body = "{\"exito\":true,\"datosAdicionales\":{\"LstRecicladores\":[{\"cant_asoc_recicladores\":\"2\",\"total_recicladores\":4}]}}"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        body = "{\"exito\":true,\"datosAdicionales\":{\"LstRecicladores\":[{\"cant_asoc_recicladores\":1,\"total_recicladores\":4},{\"cant_asoc_recicladores\":1,\"total_recicladores\":4}]}}"
                .getBytes(StandardCharsets.UTF_8);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());
    }

    @Test
    void aSlowAnswerAnAbsentServerAndABadUbigeoAreUnavailable() throws IOException {
        body = recording("lista-recicladores-150114-2024.json");
        delayMillis = 1500;
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());

        assertThat(adapter.fetch(2024, "15011")).isEqualTo(new Answer.Unavailable());
        assertThat(adapter.fetch(2024, null)).isEqualTo(new Answer.Unavailable());

        server.stop(0);
        assertThat(adapter.fetch(2024, "150114")).isEqualTo(new Answer.Unavailable());
    }
}

package pe.esgtrazabilidad.reporting.sigersolmunicipal.adapter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;

/**
 * SIGERSOL Municipal "Acceso Libre": {@code POST reporteValorizacion/listaRecicladores} with
 * {@code {"ReporteVal":{"anio","cod_departamento","cod_provincia","cod_distrito"}}}. The codes are
 * the ubigeo's pairs of digits. The rows are in {@code datosAdicionales.LstRecicladores}, one per
 * district: {@code cant_asoc_recicladores} (associations) and {@code total_recicladores}. The
 * tonnes endpoints are not used (their unit is undeclared).
 *
 * <p>Short timeouts, no retry. The body is decoded with the charset the response declares (UTF-8
 * when it declares none). Anything unexpected -- status, unreadable body, {@code exito} false,
 * several rows for one district, a missing or negative count -- is {@code Unavailable}; an empty
 * list is {@code NoData}. It never throws and never logs the body.
 */
public class HttpSigersolMunicipal implements SigersolMunicipalStats {

    private static final Logger log = LoggerFactory.getLogger(HttpSigersolMunicipal.class);
    private static final String PATH = "reporteValorizacion/listaRecicladores";

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HttpSigersolMunicipal(String baseUrl, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        String base = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.client = RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    @Override
    public Answer fetch(int year, String ubigeo) {
        if (ubigeo == null || !ubigeo.matches("\\d{6}")) {
            return new Answer.Unavailable();
        }
        Map<String, Object> body = Map.of("ReporteVal", Map.of(
                "anio", year,
                "cod_departamento", ubigeo.substring(0, 2),
                "cod_provincia", ubigeo.substring(2, 4),
                "cod_distrito", ubigeo.substring(4, 6)));
        try {
            return client.post()
                    .uri(PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status != 200) {
                            log.warn("SIGERSOL Municipal answered HTTP {}", status);
                            return new Answer.Unavailable();
                        }
                        return parse(response.getBody().readAllBytes(), charsetOf(response.getHeaders().getContentType()));
                    });
        } catch (RuntimeException exception) {
            log.warn("SIGERSOL Municipal call failed: {}", exception.getClass().getSimpleName());
            return new Answer.Unavailable();
        }
    }

    private static Charset charsetOf(MediaType contentType) {
        try {
            Charset declared = contentType == null ? null : contentType.getCharset();
            return declared == null ? StandardCharsets.UTF_8 : declared;
        } catch (RuntimeException unsupportedCharset) {
            return StandardCharsets.UTF_8;
        }
    }

    private Answer parse(byte[] bytes, Charset charset) {
        try {
            JsonNode json = objectMapper.readTree(new String(bytes, charset));
            if (json == null || !json.path("exito").asBoolean(false)) {
                log.warn("SIGERSOL Municipal answered without success");
                return new Answer.Unavailable();
            }
            JsonNode rows = json.path("datosAdicionales").path("LstRecicladores");
            if (!rows.isArray()) {
                log.warn("SIGERSOL Municipal answered without the recyclers list");
                return new Answer.Unavailable();
            }
            if (rows.isEmpty()) {
                return new Answer.NoData();
            }
            if (rows.size() > 1) {
                log.warn("SIGERSOL Municipal answered {} rows for one district", rows.size());
                return new Answer.Unavailable();
            }
            Integer associations = count(rows.get(0), "cant_asoc_recicladores");
            Integer recyclers = count(rows.get(0), "total_recicladores");
            if (associations == null || recyclers == null) {
                log.warn("SIGERSOL Municipal answered a row without usable counts");
                return new Answer.Unavailable();
            }
            return new Answer.Found(associations, recyclers);
        } catch (IOException exception) {
            log.warn("SIGERSOL Municipal answered an unreadable body");
            return new Answer.Unavailable();
        }
    }

    private static Integer count(JsonNode row, String field) {
        JsonNode node = row.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt() || node.asInt() < 0) {
            return null;
        }
        return node.asInt();
    }
}

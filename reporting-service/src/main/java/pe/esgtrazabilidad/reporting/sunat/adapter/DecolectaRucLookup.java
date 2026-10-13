package pe.esgtrazabilidad.reporting.sunat.adapter;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookup;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

/**
 * Decolecta's basic SUNAT endpoint: {@code GET /v1/sunat/ruc?numero=<ruc>} with
 * {@code Authorization: Bearer <token>}. Short timeouts and no retry loop: a
 * failure is "unavailable" and the caller decides (RPT-011). The token is sent
 * only in the header and never logged.
 *
 * <p>Status mapping: 200 with a legal name is Found; 404 and 422 are NotFound
 * (the documented 422 is "ruc no valido"; the RUC already passed our own check
 * digit, so the provider is saying it does not know it); everything else, an
 * unreadable body included, is Unavailable. The provider's answer for a
 * well-formed RUC that SUNAT does not have is not documented: confirm it on the
 * first real call.
 */
public class DecolectaRucLookup implements RucLookup {

    private static final Logger log = LoggerFactory.getLogger(DecolectaRucLookup.class);

    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DecolectaRucLookup(String baseUrl, String token, Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        this.client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .build();
    }

    @Override
    public RucLookupResult lookup(Ruc ruc) {
        try {
            return client.get()
                    .uri(uri -> uri.path("/v1/sunat/ruc").queryParam("numero", ruc.value()).build())
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 404 || status == 422) {
                            return new RucLookupResult.NotFound();
                        }
                        if (status != 200) {
                            log.warn("RUC lookup provider answered HTTP {}", status);
                            return new RucLookupResult.Unavailable();
                        }
                        return parse(response.getBody().readAllBytes());
                    });
        } catch (RuntimeException exception) {
            log.warn("RUC lookup provider call failed: {}", exception.getClass().getSimpleName());
            return new RucLookupResult.Unavailable();
        }
    }

    private RucLookupResult parse(byte[] body) {
        try {
            JsonNode json = objectMapper.readTree(body);
            String legalName = text(json, "razon_social");
            if (legalName == null) {
                log.warn("RUC lookup provider answered 200 without a legal name");
                return new RucLookupResult.Unavailable();
            }
            return new RucLookupResult.Found(
                    legalName, text(json, "estado"), text(json, "condicion"), text(json, "ubigeo"));
        } catch (IOException exception) {
            log.warn("RUC lookup provider answered an unreadable body");
            return new RucLookupResult.Unavailable();
        }
    }

    private static String text(JsonNode json, String field) {
        JsonNode node = json == null ? null : json.get(field);
        if (node == null || node.isNull() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }
}

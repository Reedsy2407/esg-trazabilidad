package pe.esgtrazabilidad.kernel.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsConfigTest {

    private final CorsConfig config = new CorsConfig();

    private static MockHttpServletRequest request(String origin, String host) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/associations");
        if (origin != null) {
            request.addHeader("Origin", origin);
        }
        request.addHeader("Host", host);
        return request;
    }

    @Test
    void theEmptyDefaultAllowsNoOrigin() {
        CorsConfiguration cors =
                config.corsConfigurationSource("").getCorsConfiguration(request("http://localhost:4200", "localhost:8081"));

        assertThat(cors.getAllowedOrigins()).isEmpty();
        assertThat(cors.checkOrigin("http://localhost:4200")).isNull();
    }

    @Test
    void aCommaSeparatedListIsTrimmedAndEachOriginAllowedExactly() {
        CorsConfiguration cors = config.corsConfigurationSource(" http://localhost:4200 , https://demo.example.org ,")
                .getCorsConfiguration(request("http://localhost:4200", "localhost:8081"));

        assertThat(cors.getAllowedOrigins()).containsExactly("http://localhost:4200", "https://demo.example.org");
        assertThat(cors.checkOrigin("https://demo.example.org")).isEqualTo("https://demo.example.org");
        assertThat(cors.checkOrigin("https://evil.example")).isNull();
    }

    @Test
    void thePolicyIsTheAgreedOne() {
        CorsConfiguration cors = config.corsConfigurationSource("http://localhost:4200")
                .getCorsConfiguration(request("http://localhost:4200", "localhost:8081"));

        assertThat(cors.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(cors.getAllowedHeaders()).containsExactly("Authorization", "Content-Type", "Accept");
        assertThat(cors.getExposedHeaders()).containsExactly("Retry-After", "Content-Disposition");
        assertThat(cors.getAllowCredentials()).isFalse();
        assertThat(cors.getMaxAge()).isEqualTo(3600L);
    }

    @Test
    void aWildcardFailsInsteadOfOpeningEveryOrigin() {
        assertThatThrownBy(() -> CorsConfig.parseAllowedOrigins("http://localhost:4200,*"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'*' is not allowed");
        assertThatThrownBy(() -> CorsConfig.parseAllowedOrigins("https://*.example.org"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anOriginWithAPathOrTrailingSlashFailsInsteadOfNeverMatching() {
        assertThatThrownBy(() -> CorsConfig.parseAllowedOrigins("http://localhost:4200/"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scheme://host[:port]");
        assertThatThrownBy(() -> CorsConfig.parseAllowedOrigins("localhost:4200")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aWildcardFailsTheApplicationStartup() {
        new ApplicationContextRunner()
                .withUserConfiguration(CorsConfig.class)
                .withPropertyValues("CORS_ALLOWED_ORIGINS=*")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseMessage("CORS_ALLOWED_ORIGINS must list explicit origins; '*' is not allowed: *"));
    }

    @Test
    void anOriginNamingTheRequestedHostIsSameSiteAndGetsNoCorsProcessing() {
        // Render: the service's own Swagger UI posting to its own API. The
        // service sees http on an internal port, but Origin and Host agree.
        CorsConfigurationSource source = config.corsConfigurationSource("");

        assertThat(source.getCorsConfiguration(
                        request("https://esg-recycler-service.onrender.com", "esg-recycler-service.onrender.com")))
                .isNull();
    }

    @Test
    void theSameHostOnADifferentPortIsStillCrossOrigin() {
        CorsConfigurationSource source = config.corsConfigurationSource("http://localhost:4200");

        assertThat(source.getCorsConfiguration(request("http://localhost:4200", "localhost:8081"))).isNotNull();
        assertThat(source.getCorsConfiguration(request("null", "localhost:8081"))).isNotNull();
    }
}

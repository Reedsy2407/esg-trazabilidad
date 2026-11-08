package pe.esgtrazabilidad.kernel.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CA-3 and the kernel part of CA-14, over a real filter chain with real signed
 * tokens: role rules (hasRole / hasAnyRole), 403 AUTH-000 for the wrong role,
 * 401 AUTH-000 with a JSON body, and the consent / location filter (409 AUTH-019).
 */
@SpringJUnitWebConfig(RoleAndConsentSecurityTest.TestApp.class)
@TestPropertySource(properties = "JWT_SECRET=" + RoleAndConsentSecurityTest.SECRET)
class RoleAndConsentSecurityTest {

    static final String SECRET = "role-consent-test-secret-at-least-32-bytes-long!!";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(JwtSecurityConfig.class)
    static class TestApp {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SecurityFilterChain chain(
                HttpSecurity http,
                AuthenticationEntryPoint jwtAuthenticationEntryPoint,
                AccessDeniedHandler jwtAccessDeniedHandler,
                ObjectMapper objectMapper)
                throws Exception {
            return http.csrf(CsrfConfigurer::disable)
                    .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .addFilterAfter(
                            new FieldAccessFilter(
                                    objectMapper,
                                    new AntPathRequestMatcher("/field/**"),
                                    new AntPathRequestMatcher("/field/gps")),
                            BearerTokenAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.requestMatchers("/staff/**")
                            .hasAnyRole("STAFF", "ADMIN")
                            .requestMatchers("/field/**")
                            .hasRole("RECYCLER")
                            .anyRequest()
                            .authenticated())
                    .exceptionHandling(handling -> handling
                            .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                            .accessDeniedHandler(jwtAccessDeniedHandler))
                    .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults())
                            .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                    .build();
        }

        @RestController
        static class ProbeController {
            @GetMapping("/staff/ping")
            String staff() {
                return "staff";
            }

            @GetMapping("/field/ping")
            String field() {
                return "field";
            }

            @GetMapping("/field/gps")
            String gps() {
                return "gps";
            }
        }
    }

    @Autowired
    private WebApplicationContext context;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ---- CA-3 and role rules ----

    @Test
    void staffTokenOnARecyclerRouteIs403Auth000() throws Exception {
        forbidden(get("/field/ping", staffToken("STAFF")));
        forbidden(get("/field/ping", staffToken("ADMIN")));
    }

    @Test
    void recyclerTokenOnAStaffRouteIs403Auth000() throws Exception {
        forbidden(get("/staff/ping", recyclerToken(1, true)));
    }

    @Test
    void staffAndAdminReachStaffRoutes() throws Exception {
        get("/staff/ping", staffToken("STAFF")).andExpect(status().isOk()).andExpect(content().string("staff"));
        get("/staff/ping", staffToken("ADMIN")).andExpect(status().isOk());
    }

    @Test
    void aTokenWithoutRoleHasNoAuthorityAndIs403OnRoleRoutes() throws Exception {
        String noRole = sign(claims(null, new HashMap<>()), SECRET, 60);
        forbidden(get("/staff/ping", noRole));
        forbidden(get("/field/ping", noRole));
    }

    // ---- CA-17: 401 with JSON body ----

    @Test
    void missingExpiredOrTamperedTokensAre401Auth000WithABody() throws Exception {
        String expired = sign(claims("RECYCLER", recyclerExtra(1, true)), SECRET, -120);
        String wrongKey = sign(claims("RECYCLER", recyclerExtra(1, true)), "another-secret-also-at-least-32-bytes-long!!", 60);

        unauthorized(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/field/ping")));
        unauthorized(get("/field/ping", expired));
        unauthorized(get("/field/ping", wrongKey));
    }

    // ---- CA-14: the three combinations ----

    @Test
    void withoutGeneralConsentFieldRoutesAre409Auth019() throws Exception {
        conflict(get("/field/ping", recyclerToken(null, false)));
        conflict(get("/field/gps", recyclerToken(null, false)));
    }

    @Test
    void generalConsentWithoutLocationOpensNonGpsRoutesAndBlocksGps() throws Exception {
        get("/field/ping", recyclerToken(1, false)).andExpect(status().isOk());
        conflict(get("/field/gps", recyclerToken(1, false)));
    }

    @Test
    void bothConsentsOpenEverything() throws Exception {
        get("/field/ping", recyclerToken(2, true)).andExpect(status().isOk());
        get("/field/gps", recyclerToken(2, true)).andExpect(status().isOk());
    }

    @Test
    void locationFlagWithoutGeneralConsentStillBlocks() throws Exception {
        conflict(get("/field/gps", recyclerToken(null, true)));
    }

    // ---- helper used by routes whose GPS use depends on the body ----

    @Test
    void claimsHelperAppliesTheSameRules() {
        UUID id = UUID.randomUUID();
        RecyclerClaims none = new RecyclerClaims(id, id, id, id, null, false);
        RecyclerClaims general = new RecyclerClaims(id, id, id, id, 1, false);
        RecyclerClaims both = new RecyclerClaims(id, id, id, id, 1, true);

        assertThatThrownBy(none::requireConsent).hasMessage("Consentimiento requerido");
        general.requireConsent();
        assertThatThrownBy(general::requireLocationConsent).hasMessage("Consentimiento de ubicación requerido");
        both.requireLocationConsent();
        assertThat(RecyclerClaims.ConsentError.CONSENT_REQUIRED.getCode()).isEqualTo("AUTH-019");
        assertThat(RecyclerClaims.ConsentError.CONSENT_REQUIRED.getStatus().value()).isEqualTo(409);
    }

    // ---- helpers ----

    private ResultActions get(String path, String token) throws Exception {
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
                .header("Authorization", "Bearer " + token));
    }

    private static void forbidden(ResultActions result) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH-000"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para esta acción"));
    }

    private static void unauthorized(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH-000"))
                .andExpect(jsonPath("$.detail").value("Token faltante o inválido"));
    }

    private static void conflict(ResultActions result) throws Exception {
        result.andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AUTH-019"));
    }

    private static String staffToken(String role) throws Exception {
        return sign(claims(role, new HashMap<>()), SECRET, 60);
    }

    private static String recyclerToken(Integer consentVersion, boolean location) throws Exception {
        return sign(claims("RECYCLER", recyclerExtra(consentVersion, location)), SECRET, 60);
    }

    private static Map<String, Object> recyclerExtra(Integer consentVersion, boolean location) {
        Map<String, Object> extra = new HashMap<>();
        extra.put("rid", UUID.randomUUID().toString());
        extra.put("aid", UUID.randomUUID().toString());
        extra.put("did", UUID.randomUUID().toString());
        if (consentVersion != null) {
            extra.put("cv", consentVersion);
        }
        if (location) {
            extra.put("lc", true);
        }
        return extra;
    }

    private static JWTClaimsSet.Builder claims(String role, Map<String, Object> extra) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString());
        if (role != null) {
            builder.claim("role", role);
        }
        extra.forEach(builder::claim);
        return builder;
    }

    private static String sign(JWTClaimsSet.Builder builder, String secret, int validSeconds) throws Exception {
        builder.expirationTime(new Date(System.currentTimeMillis() + validSeconds * 1000L));
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
